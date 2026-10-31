package com.nuvio.app.features.tvremote

import com.nuvio.app.getPlatform
import com.nuvio.app.features.tvremote.protocol.RemoteClientInfo
import com.nuvio.app.features.tvremote.protocol.RemoteLaunchTarget
import com.nuvio.app.features.tvremote.protocol.RemoteTvApp
import kotlinx.coroutines.CompletableDeferred
import com.nuvio.app.features.tvremote.protocol.RemoteCommand
import com.nuvio.app.features.tvremote.protocol.RemoteErrorCode
import com.nuvio.app.features.tvremote.protocol.RemoteKey
import com.nuvio.app.features.tvremote.protocol.RemoteMessage
import com.nuvio.app.features.tvremote.protocol.RemoteMessageType
import com.nuvio.app.features.tvremote.protocol.RemotePlayRequest
import com.nuvio.app.features.tvremote.protocol.RemotePlaybackState
import com.nuvio.app.features.tvremote.protocol.RemotePlaybackStatus
import com.nuvio.app.features.tvremote.protocol.RemoteSubtitleStyle
import com.nuvio.app.isIos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.builtins.ListSerializer
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val INVITE_ID_PREFIX = "invite-"

/**
 * Single source of truth for the "TV-Fernbedienung" feature: settings, paired TVs,
 * the live session with the selected TV, and pairing.
 */

object TvRemoteRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val pairedListSerializer = ListSerializer(PairedTv.serializer())

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _pairedTvs = MutableStateFlow<List<PairedTv>>(emptyList())
    val pairedTvs: StateFlow<List<PairedTv>> = _pairedTvs.asStateFlow()

    /** The TV the app stays connected to (remote, now-playing bar); `null` means none. */
    private val _activeTvId = MutableStateFlow<String?>(null)
    val activeTvId: StateFlow<String?> = _activeTvId.asStateFlow()

    /**
     * Whether picked streams go to [activeTvId]. Deliberately not persisted: after every
     * app start, playback stays on the phone until the user picks the TV in the cast sheet.
     */
    private val _castArmed = MutableStateFlow(false)
    val castArmed: StateFlow<Boolean> = _castArmed.asStateFlow()

    /** Cast sheet choice: a TV arms casting to it, `null` ("this phone") disarms but stays connected. */
    fun selectCastTarget(tvId: String?) {
        if (tvId != null) selectTarget(tvId)
        _castArmed.value = tvId != null && activeTv() != null
    }

    private val _discoveredTvs = MutableStateFlow<List<DiscoveredTv>>(emptyList())
    val discoveredTvs: StateFlow<List<DiscoveredTv>> = _discoveredTvs.asStateFlow()

    private val _session = MutableStateFlow(TvSessionState())
    val session: StateFlow<TvSessionState> = _session.asStateFlow()

    private val _pairing = MutableStateFlow<TvPairingState>(TvPairingState.Idle)
    val pairing: StateFlow<TvPairingState> = _pairing.asStateFlow()

    private var loaded = false
    private var link: TvLink? = null
    private var linkTvId: String? = null
    private var linkJobs: List<Job> = emptyList()
    private var pairingLink: TvLink? = null
    private var pairingJobs: List<Job> = emptyList()
    private var clientId: String = ""
    private var lastPairingTarget: Pair<String, Int>? = null

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        _enabled.value = TvRemoteStorage.loadEnabled() ?: false
        _pairedTvs.value = TvRemoteStorage.loadPairedTvsJson()
            ?.let { json -> runCatching { tvRemoteJson.decodeFromString(pairedListSerializer, json) }.getOrNull() }
            .orEmpty()
        _activeTvId.value = TvRemoteStorage.loadActiveTvId()?.takeIf { id -> _pairedTvs.value.any { it.id == id } }
        clientId = TvRemoteStorage.loadClientId() ?: newId().also(TvRemoteStorage::saveClientId)
        syncLink()
    }

    fun setEnabled(enabled: Boolean) {
        ensureLoaded()
        _enabled.value = enabled
        TvRemoteStorage.saveEnabled(enabled)
        TvRemoteLog.i("feature ${if (enabled) "enabled" else "disabled"}")
        syncLink()
    }

    fun selectTarget(tvId: String?) {
        ensureLoaded()
        val resolved = tvId?.takeIf { id -> _pairedTvs.value.any { it.id == id } }
        _activeTvId.value = resolved
        TvRemoteStorage.saveActiveTvId(resolved)
        syncLink()
    }

    fun activeTv(): PairedTv? = _pairedTvs.value.firstOrNull { it.id == _activeTvId.value }

    /** Paired TVs for the account's settings sync, so other devices skip pairing. Demo TVs stay local. */
    fun exportForSync(): String {
        ensureLoaded()
        val tvs = _pairedTvs.value.filterNot(PairedTv::demo)
        if (tvs.isEmpty()) return ""
        return tvRemoteJson.encodeToString(
            TvRemoteSyncPayload.serializer(),
            TvRemoteSyncPayload(enabled = _enabled.value, activeTvId = _activeTvId.value, pairedTvs = tvs),
        )
    }

    /**
     * Adds the account's TVs: unknown ones are added, known ones take the more recently seen copy.
     * Nothing is removed, and an empty payload changes nothing.
     */
    fun mergeFromSync(payload: String) {
        if (payload.isBlank()) return
        ensureLoaded()
        val remote = runCatching { tvRemoteJson.decodeFromString(TvRemoteSyncPayload.serializer(), payload) }
            .getOrNull() ?: return
        if (remote.pairedTvs.isEmpty()) return
        val merged = _pairedTvs.value.associateBy { it.id }.toMutableMap()
        remote.pairedTvs.forEach { tv ->
            val local = merged[tv.id]
            if (local == null || tv.lastSeenAtMs > local.lastSeenAtMs) merged[tv.id] = tv
        }
        val tvs = merged.values.toList()
        if (tvs != _pairedTvs.value) savePairedTvs(tvs)
        if (_activeTvId.value == null) {
            (remote.activeTvId?.takeIf { id -> tvs.any { it.id == id } } ?: tvs.firstOrNull()?.id)?.let(::selectTarget)
        }
        if (remote.enabled && !_enabled.value) setEnabled(true) else syncLink()
    }

    fun removeTv(tvId: String) {
        ensureLoaded()
        savePairedTvs(_pairedTvs.value.filterNot { it.id == tvId })
        if (_activeTvId.value == tvId) selectTarget(null)
    }

    fun renameTv(tvId: String, name: String) {
        val trimmed = name.trim().takeIf { it.isNotEmpty() } ?: return
        savePairedTvs(_pairedTvs.value.map { if (it.id == tvId) it.copy(name = trimmed) else it })
    }

    // ------------------------------------------------------------------ pairing

    fun startPairing(host: String, port: Int = TV_REMOTE_DEFAULT_PORT) {
        ensureLoaded()
        cancelPairing()
        val demo = host == DEMO_TV_HOST
        lastPairingTarget = host to port
        TvRemoteLog.i("pairing start ${if (demo) "demo" else "$host:$port"}")
        _pairing.value = TvPairingState.Connecting
        val pairLink = if (demo) DemoTvLink(scope) else WebSocketTvLink(host, port, scope)
        pairingLink = pairLink
        var tvName = host
        var tvModel: String? = null
        var tvId = host
        var tvMac: String? = null
        pairingJobs = listOf(
            scope.launch {
                val opened = withTimeoutOrNull(PAIRING_CONNECT_TIMEOUT_MS) {
                    pairLink.status.first { it == TvLinkStatus.Open }
                }
                if (opened == null) {
                    _pairing.value = TvPairingState.Failed("unreachable")
                    TvRemoteLog.w("pairing: $host:$port not reachable")
                    closePairingLink()
                    return@launch
                }
                pairLink.send(helloMessage(token = null))
            },
            scope.launch {
                pairLink.messages.collect { message ->
                    when (message.type) {
                        RemoteMessageType.WELCOME -> {
                            message.tv?.let { tv ->
                                tvName = tv.name
                                tvModel = tv.model
                                tvId = tv.id
                                tvMac = tv.mac
                            }
                            _pairing.value = TvPairingState.AwaitingCode(tvName)
                            pairLink.send(RemoteMessage(type = RemoteMessageType.PAIR_BEGIN))
                        }
                        RemoteMessageType.PAIR_OK -> {
                            val token = message.token ?: return@collect
                            val tv = PairedTv(
                                id = tvId,
                                name = tvName,
                                host = host,
                                port = port,
                                token = token,
                                model = tvModel,
                                lastSeenAtMs = now(),
                                demo = demo,
                                mac = tvMac,
                            )
                            savePairedTvs(_pairedTvs.value.filterNot { it.id == tv.id } + tv)
                            TvRemoteLog.i("paired with ${tv.name} (${tv.id})")
                            _pairing.value = TvPairingState.Paired(tv)
                            closePairingLink()
                            if (_activeTvId.value == null) selectTarget(tv.id) else syncLink()
                        }
                        RemoteMessageType.ERROR -> {
                            if (message.code == RemoteErrorCode.BAD_CODE) {
                                _pairing.value = TvPairingState.AwaitingCode(tvName, wrongCode = true)
                            } else {
                                _pairing.value = TvPairingState.Failed(message.message ?: message.code ?: "error")
                            }
                        }
                    }
                }
            },
        )
    }

    fun confirmPairingCode(code: String) {
        val current = _pairing.value as? TvPairingState.AwaitingCode ?: return
        val sent = pairingLink?.send(RemoteMessage(type = RemoteMessageType.PAIR_CONFIRM, code = code.trim())) == true
        _pairing.value = if (sent) TvPairingState.Verifying else current.copy(wrongCode = false).also {
            TvRemoteLog.w("pairing: connection lost before code was sent")
        }
    }

    /** Restarts pairing with the last host, e.g. after "TV not reachable". */
    fun retryPairing() {
        val (host, port) = lastPairingTarget ?: return
        startPairing(host, port)
    }

    fun cancelPairing() {
        closePairingLink()
        _pairing.value = TvPairingState.Idle
    }

    private fun closePairingLink() {
        pairingJobs.forEach { it.cancel() }
        pairingJobs = emptyList()
        pairingLink?.close()
        pairingLink = null
    }

    // ------------------------------------------------------------------ session

    /** Opens (or closes) the live connection so it matches settings + selected TV. */
    private fun syncLink() {
        val tv = activeTv()?.takeIf { _enabled.value }
        if (tv?.id == linkTvId && link != null) return
        linkJobs.forEach { it.cancel() }
        linkJobs = emptyList()
        link?.close()
        link = null
        linkTvId = tv?.id
        if (tv == null) {
            _session.value = TvSessionState()
            return
        }
        TvRemoteLog.i("connecting to ${tv.name} (${if (tv.demo) "demo" else "${tv.host}:${tv.port}"})")
        val newLink = if (tv.demo) DemoTvLink(scope) else WebSocketTvLink(tv.host, tv.port, scope)
        link = newLink
        _session.value = TvSessionState(tvId = tv.id, connection = TvConnectionStatus.Connecting)
        linkJobs = listOf(
            scope.launch {
                newLink.status.collect { status ->
                    when (status) {
                        TvLinkStatus.Open -> newLink.send(helloMessage(token = tv.token))
                        TvLinkStatus.Connecting -> updateConnection(TvConnectionStatus.Connecting)
                        TvLinkStatus.Closed -> {
                            updateConnection(TvConnectionStatus.Unreachable)
                            _session.update { it.copy(party = null) }
                            // The TV may just have a new IP address: look for it on the LAN.
                            if (!tv.demo) refreshHostsBriefly()
                        }
                    }
                }
            },
            scope.launch {
                newLink.messages.collect { message -> handleSessionMessage(tv, newLink, message) }
            },
        )
    }

    private fun updateConnection(status: TvConnectionStatus) {
        _session.update { current ->
            if (current.connection == TvConnectionStatus.Unauthorized && status != TvConnectionStatus.Connected) {
                current
            } else {
                current.copy(connection = status)
            }
        }
    }

    private fun handleSessionMessage(tv: PairedTv, sessionLink: TvLink, message: RemoteMessage) {
        when (message.type) {
            RemoteMessageType.WELCOME -> {
                if (message.paired == true) {
                    _session.update { it.copy(connection = TvConnectionStatus.Connected, lastError = null) }
                    watchTvVolume()
                    val mac = message.tv?.mac
                    savePairedTvs(_pairedTvs.value.map { if (it.id == tv.id) it.copy(lastSeenAtMs = now(), mac = mac ?: it.mac) else it })
                    sessionLink.send(RemoteMessage(type = RemoteMessageType.STATE_GET))
                } else {
                    TvRemoteLog.w("${tv.name} rejected our token, re-pairing needed")
                    _session.update { it.copy(connection = TvConnectionStatus.Unauthorized) }
                }
            }
            RemoteMessageType.STATE -> {
                val playback = message.playback ?: return
                _session.update {
                    it.copy(playback = playback, receivedAtMs = now(), tvPoweredOn = message.tvPoweredOn ?: it.tvPoweredOn)
                }
            }
            RemoteMessageType.PARTY_STATE -> _session.update { current ->
                // A new party starts with a clean invite list.
                val sameParty = current.party?.code == message.party?.code
                current.copy(party = message.party, partyInvites = if (sameParty) current.partyInvites else emptyMap())
            }.also {
                if (message.party != null && invitesAwaitingParty.isNotEmpty()) {
                    partyStartTimeout?.cancel()
                    val waiting = invitesAwaitingParty.toList()
                    invitesAwaitingParty.clear()
                    waiting.forEach(::sendPartyInvite)
                }
            }
            RemoteMessageType.PARTY_INVITE_RESULT -> {
                val code = message.friendCode ?: return
                val status = if (message.ok == true) {
                    TvPartyInviteStatus.Sent
                } else {
                    TvPartyInviteStatus.Failed(message.code ?: "failed")
                }
                _session.update { it.copy(partyInvites = it.partyInvites + (code to status)) }
            }
            RemoteMessageType.SSAP_KEY -> {
                recentTvApps = message.recentApps.orEmpty()
                tvApiKeyWaiter?.complete(message.key)
            }
            RemoteMessageType.ERROR -> {
                TvRemoteLog.w("tv error ${message.code}: ${message.message.orEmpty()}")
                if (message.id == SSAP_KEY_ID) {
                    tvApiKeyWaiter?.complete(null)
                    return
                }
                val invitedCode = message.id?.takeIf { it.startsWith(INVITE_ID_PREFIX) }?.removePrefix(INVITE_ID_PREFIX)
                if (invitedCode != null && message.code != RemoteErrorCode.NOT_PAIRED) {
                    // An older TV app answers party.invite with unknown_type; the daemon with no_player.
                    val failure = TvPartyInviteStatus.Failed(message.code ?: "failed")
                    _session.update { it.copy(partyInvites = it.partyInvites + (invitedCode to failure)) }
                    return
                }
                if (message.code == RemoteErrorCode.NOT_PAIRED) {
                    _session.update { it.copy(connection = TvConnectionStatus.Unauthorized) }
                } else {
                    _session.update { it.copy(lastError = message.message ?: message.code) }
                }
            }
        }
    }

    /** Sends a play request to the active TV, waiting briefly for the connection. */
    fun play(request: RemotePlayRequest) {
        ensureLoaded()
        syncLink()
        val target = activeTv() ?: return
        TvRemoteLog.i("play on ${target.name}: ${request.media.title} ${request.media.season ?: ""}x${request.media.episode ?: ""}")
        _session.update {
            it.copy(
                playback = RemotePlaybackState(
                    status = RemotePlaybackStatus.Loading,
                    positionMs = request.startPositionMs,
                    media = request.media,
                ),
                receivedAtMs = now(),
                lastError = null,
            )
        }
        scope.launch {
            // Always wake: the daemon stays connected while the TV is in standby and holds the
            // play until the TV is on.
            val mac = target.mac?.takeIf { !target.demo }
            val waker = mac?.let { scope.launch { withTimeoutOrNull(WAKE_CONNECT_TIMEOUT_MS) { wakeTv(target, it) } } }
            val connected = withTimeoutOrNull(if (waker != null) WAKE_CONNECT_TIMEOUT_MS else PLAY_CONNECT_TIMEOUT_MS) {
                _session.first { it.connection == TvConnectionStatus.Connected }
            }
            if (connected == null) waker?.cancel()
            val sent = connected != null && link?.send(RemoteMessage(type = RemoteMessageType.PLAY, request = request)) == true
            if (!sent) {
                TvRemoteLog.w("play: ${target.name} not connected")
                _session.update {
                    it.copy(
                        playback = it.playback.copy(status = RemotePlaybackStatus.Error),
                        lastError = "not_connected",
                    )
                }
            }
        }
    }

    /**
     * Sends a few magic packets (Wi-Fi drops single broadcasts) and keeps going until the TV is
     * connected; reconnects right away instead of waiting out the link's backoff.
     */
    private suspend fun wakeTv(tv: PairedTv, mac: String) {
        val packet = wakeOnLanPacket(mac) ?: return TvRemoteLog.w("bad mac for ${tv.name}: $mac")
        TvRemoteLog.i("waking ${tv.name} via Wake-on-LAN")
        _session.update { it.copy(waking = true) }
        try {
            var sent = 0
            // Until the TV answers with its screen on: in standby its hub may already be reachable.
            while (sent < WAKE_MIN_PACKETS || !_session.value.isTvOn) {
                runCatching { sendWakeOnLan(packet, tv.host) }.onFailure { TvRemoteLog.w("wol send failed", it) }
                sent += 1
                if (_session.value.connection == TvConnectionStatus.Unreachable) reconnect()
                delay(WAKE_RESEND_MS)
            }
        } finally {
            _session.update { it.copy(waking = false) }
        }
    }

    private var powerOnJob: Job? = null

    /**
     * Power key: switches the TV off through its API while it is on, otherwise wakes it with
     * Wake-on-LAN (like LG's own app), which also works while the phone has no connection.
     */
    fun togglePower() {
        ensureLoaded()
        val tv = activeTv() ?: return
        if (_session.value.isTvOn) {
            _session.update { it.copy(tvPoweredOn = false) }
            scope.launch { TvApi.pressKey(RemoteKey.POWER) }
            return
        }
        val mac = tv.mac?.takeIf { !tv.demo } ?: return TvRemoteLog.w("power on: no MAC known for ${tv.name}")
        if (powerOnJob?.isActive == true) return
        syncLink()
        powerOnJob = scope.launch { withTimeoutOrNull(WAKE_CONNECT_TIMEOUT_MS) { wakeTv(tv, mac) } }
    }

    fun togglePause() {
        val playback = _session.value.playback
        val optimistic = when (playback.status) {
            RemotePlaybackStatus.Playing -> RemotePlaybackStatus.Paused
            RemotePlaybackStatus.Paused -> RemotePlaybackStatus.Playing
            else -> null
        }
        if (optimistic != null) {
            val now = now()
            _session.update {
                it.copy(
                    playback = it.playback.copy(status = optimistic, positionMs = it.positionAt(now)),
                    receivedAtMs = now,
                )
            }
        }
        command(RemoteCommand.TOGGLE)
    }

    fun seekTo(positionMs: Long) {
        _session.update {
            it.copy(playback = it.playback.copy(positionMs = positionMs.coerceAtLeast(0L)), receivedAtMs = now())
        }
        command(RemoteCommand.SEEK, positionMs = positionMs.coerceAtLeast(0L))
    }

    fun seekBy(deltaMs: Long) {
        val session = _session.value
        val duration = session.playback.durationMs
        val target = (session.positionAt(now()) + deltaMs).coerceAtLeast(0L)
        seekTo(if (duration > 0L) target.coerceAtMost(duration) else target)
    }

    fun stop() {
        command(RemoteCommand.STOP)
        _session.update { it.copy(playback = RemotePlaybackState()) }
    }

    fun playNext() = command(RemoteCommand.NEXT)

    /** Skips the current intro/recap/outro, like the skip button on the TV. */
    fun skipSegment() {
        _session.update { it.copy(playback = it.playback.copy(skip = null)) }
        command(RemoteCommand.SKIP)
    }

    fun selectAudioTrack(trackId: String) = command(RemoteCommand.AUDIO, trackId = trackId)

    /** `null` turns subtitles off. */
    fun selectSubtitleTrack(trackId: String?) = command(RemoteCommand.SUBTITLE, trackId = trackId)

    fun setSubtitleDelay(delayMs: Long) {
        _session.update { it.copy(playback = it.playback.copy(subtitleDelayMs = delayMs)) }
        command(RemoteCommand.SUBTITLE_DELAY, delayMs = delayMs)
    }

    /** Presses a button of the TV's remote (see [RemoteKey]), directly on the TV's API. */
    fun pressKey(key: String) {
        if (key == RemoteKey.POWER) return togglePower()
        scope.launch { TvApi.pressKey(key) }
    }

    /** Opens Home, Settings, an input or an app on the TV (see [RemoteLaunchTarget]). */
    fun launchOnTv(target: String) {
        scope.launch { TvApi.launch(SYSTEM_APPS[target] ?: target) }
    }

    /** Loads the TV's apps, most recently opened first (the daemon keeps that history). */
    fun requestApps() {
        scope.launch {
            fetchTvApiKey(refresh = false)
            val recent = recentTvApps
            val apps = TvApi.launchPoints().sortedWith(
                compareBy({ recent.indexOf(it.id).let { index -> if (index < 0) Int.MAX_VALUE else index } }, { it.title.lowercase() }),
            )
            _session.update { current -> current.copy(apps = apps.map { RemoteTvApp(it.id, it.title, it.iconUrl) }) }
        }
    }

    private val SYSTEM_APPS = mapOf(
        RemoteLaunchTarget.HOME to "com.webos.app.home",
        RemoteLaunchTarget.SETTINGS to "com.palm.app.settings",
    ) + (1..4).associate { RemoteLaunchTarget.hdmi(it) to "com.webos.app.hdmi$it" }

    private val tvApiKeyMutex = Mutex()
    private var tvApiKeyWaiter: CompletableDeferred<String?>? = null
    private var recentTvApps: List<String> = emptyList()

    /** The shared TV API key: stored per TV, else (or on [refresh]) asked from the daemon. */
    internal suspend fun tvApiKey(refresh: Boolean): String? {
        if (!refresh) activeTv()?.tvApiKey?.let { return it }
        return fetchTvApiKey(refresh)
    }

    private suspend fun fetchTvApiKey(refresh: Boolean): String? = tvApiKeyMutex.withLock {
        val tv = activeTv() ?: return@withLock null
        val waiter = CompletableDeferred<String?>()
        tvApiKeyWaiter = waiter
        send(RemoteMessage(type = RemoteMessageType.SSAP_KEY, id = SSAP_KEY_ID, refresh = refresh.takeIf { it }))
        // The first registration waits until someone allows the remote on the TV.
        val key = withTimeoutOrNull(SSAP_KEY_TIMEOUT_MS) { waiter.await() }
        tvApiKeyWaiter = null
        if (key != null && key != tv.tvApiKey) {
            savePairedTvs(_pairedTvs.value.map { if (it.id == tv.id) it.copy(tvApiKey = key) else it })
        }
        key
    }

    /** Changes the TV's subtitle appearance; only the fields set in [style] change. */
    fun setSubtitleStyle(style: RemoteSubtitleStyle) {
        _session.update { current ->
            val old = current.playback.subtitleStyle ?: RemoteSubtitleStyle()
            val merged = RemoteSubtitleStyle(
                fontSize = style.fontSize ?: old.fontSize,
                textColor = style.textColor ?: old.textColor,
                textOpacity = style.textOpacity ?: old.textOpacity,
                bold = style.bold ?: old.bold,
                outlineEnabled = style.outlineEnabled ?: old.outlineEnabled,
                outlineColor = style.outlineColor ?: old.outlineColor,
                verticalOffset = style.verticalOffset ?: old.verticalOffset,
            )
            current.copy(playback = current.playback.copy(subtitleStyle = merged))
        }
        send(RemoteMessage(type = RemoteMessageType.CMD, action = RemoteCommand.SUBTITLE_STYLE, style = style))
    }

    fun startParty() = command(RemoteCommand.PARTY_START)

    fun endParty() = command(RemoteCommand.PARTY_END)

    fun setSpeed(rate: Float) {
        _session.update { it.copy(playback = it.playback.copy(rate = rate)) }
        command(RemoteCommand.SPEED, rate = rate)
    }

    // Volume goes straight to the TV's API; the level shown comes from its volume subscription.
    fun volumeUp() {
        scope.launch { TvApi.volumeUp() }
    }

    fun volumeDown() {
        scope.launch { TvApi.volumeDown() }
    }

    fun toggleMute() {
        val muted = !_session.value.volume.muted
        _session.update { it.copy(volume = it.volume.copy(muted = muted)) }
        scope.launch { TvApi.setMute(muted) }
    }

    private var volumeSendJob: Job? = null
    private var localVolumeUntilMs = 0L

    /**
     * Absolute volume 0–100, e.g. from a slider. While the user drags, only the latest value is
     * sent (coalesced) and shown right away; TV reports are ignored for a moment, otherwise
     * older in-flight levels would make the slider jump back.
     */
    fun setVolume(level: Int) {
        val target = level.coerceIn(0, MAX_VOLUME)
        localVolumeUntilMs = now() + LOCAL_VOLUME_HOLD_MS
        _session.update { it.copy(volume = it.volume.copy(level = target)) }
        if (volumeSendJob?.isActive == true) return
        volumeSendJob = scope.launch {
            delay(VOLUME_COALESCE_MS)
            TvApi.setVolume(_session.value.volume.level ?: target)
        }
    }

    /** One step from a hardware key, relative to what the user currently sees. */
    fun stepVolume(direction: Int) {
        val current = _session.value.volume.level
        if (current == null) {
            if (direction > 0) volumeUp() else if (direction < 0) volumeDown()
            return
        }
        setVolume(current + direction.coerceIn(-1, 1))
    }

    private fun watchTvVolume() {
        scope.launch {
            TvApi.watchVolume { level, muted ->
                _session.update {
                    // Right after a local change the TV may still report older levels.
                    val shown = if (now() < localVolumeUntilMs) it.volume.level else level
                    it.copy(volume = TvVolumeState(level = shown, muted = muted))
                }
            }
        }
    }

    /** Asks the TV to invite [friendCode] to its watch party; the TV sends it with its party code. */
    // Invites made while the TV is in no party: they start one and go out once it exists.
    private val invitesAwaitingParty = mutableSetOf<String>()
    private var partyStartTimeout: Job? = null

    fun inviteToTvParty(friendCode: String) {
        if (_session.value.party != null) {
            sendPartyInvite(friendCode)
            return
        }
        invitesAwaitingParty += friendCode
        _session.update { it.copy(partyInvites = it.partyInvites + (friendCode to TvPartyInviteStatus.Sending)) }
        if (partyStartTimeout?.isActive == true) return
        startParty()
        partyStartTimeout = scope.launch {
            delay(PARTY_START_TIMEOUT_MS)
            val failed = invitesAwaitingParty.toList()
            invitesAwaitingParty.clear()
            _session.update { current ->
                current.copy(partyInvites = current.partyInvites + failed.map { it to TvPartyInviteStatus.Failed("no_party") })
            }
        }
    }

    private fun sendPartyInvite(friendCode: String) {
        val sent = link?.send(
            RemoteMessage(type = RemoteMessageType.PARTY_INVITE, id = INVITE_ID_PREFIX + friendCode, friendCode = friendCode),
        ) == true
        val status = if (sent) TvPartyInviteStatus.Sending else TvPartyInviteStatus.Failed("not_connected")
        _session.update { it.copy(partyInvites = it.partyInvites + (friendCode to status)) }
    }

    /** Forces a reconnect, e.g. after the user tapped "retry". */
    fun reconnect() {
        linkTvId = null
        syncLink()
    }


    private fun command(
        action: String,
        positionMs: Long? = null,
        deltaMs: Long? = null,
        trackId: String? = null,
        delayMs: Long? = null,
        rate: Float? = null,
    ) {
        send(
            RemoteMessage(
                type = RemoteMessageType.CMD,
                action = action,
                positionMs = positionMs,
                deltaMs = deltaMs,
                trackId = trackId,
                delayMs = delayMs,
                rate = rate,
            ),
        )
    }

    private fun send(message: RemoteMessage) {
        val sent = link?.send(message) == true
        if (!sent) TvRemoteLog.w("dropped ${message.type}/${message.action.orEmpty()} (not connected)")
    }

    // ------------------------------------------------------------------ helpers

    fun addDemoTv() = startPairing(DEMO_TV_HOST)

    // ------------------------------------------------------------ discovery

    private var discoveryUsers = 0
    private var briefDiscoveryJob: Job? = null
    private var briefDiscoveryAtMs = 0L
    private val discoveryMutex = Mutex()
    private val discoveredByService = MutableStateFlow<Map<String, DiscoveredTv>>(emptyMap())

    val isDiscoverySupported: Boolean
        get() = TvDiscoveryPlatform.isSupported

    /** Starts LAN discovery; pair every call with [stopDiscovery]. Main thread. */
    fun startDiscovery() {
        ensureLoaded()
        discoveryUsers += 1
        if (discoveryUsers == 1) {
            briefDiscoveryJob?.cancel()
            briefDiscoveryJob = null
            TvDiscoveryPlatform.start(onFound = ::onTvFound, onLost = ::onTvLost)
        }
    }

    fun stopDiscovery() {
        if (discoveryUsers == 0) return
        discoveryUsers -= 1
        if (discoveryUsers == 0) {
            TvDiscoveryPlatform.stop()
            discoveredByService.value = emptyMap()
            _discoveredTvs.value = emptyList()
        }
    }

    private fun refreshHostsBriefly() {
        if (!TvDiscoveryPlatform.isSupported || discoveryUsers > 0 || briefDiscoveryJob?.isActive == true) return
        if (now() - briefDiscoveryAtMs < BRIEF_DISCOVERY_COOLDOWN_MS) return
        briefDiscoveryAtMs = now()
        briefDiscoveryJob = scope.launch(Dispatchers.Main) {
            startDiscovery()
            try {
                delay(BRIEF_DISCOVERY_MS)
            } finally {
                stopDiscovery()
            }
        }
    }

    private fun onTvFound(serviceName: String, tv: DiscoveredTv) {
        scope.launch {
            discoveryMutex.withLock {
                val services = discoveredByService.value + (serviceName to tv)
                discoveredByService.value = services
                _discoveredTvs.value = services.values.distinctBy { it.id }.sortedBy { it.name }
                // A paired TV that moved to a new address (DHCP) is updated in place.
                val paired = _pairedTvs.value.firstOrNull { it.id == tv.id && !it.demo } ?: return@withLock
                if (paired.host == tv.host && paired.port == tv.port) return@withLock
                TvRemoteLog.i("${paired.name} moved to ${tv.host}:${tv.port}")
                savePairedTvs(_pairedTvs.value.map { if (it.id == tv.id) it.copy(host = tv.host, port = tv.port) else it })
                if (_activeTvId.value == tv.id) reconnect()
            }
        }
    }

    private fun onTvLost(serviceName: String) {
        scope.launch {
            discoveryMutex.withLock {
                val services = discoveredByService.value - serviceName
                discoveredByService.value = services
                _discoveredTvs.value = services.values.distinctBy { it.id }.sortedBy { it.name }
            }
        }
    }

    private fun helloMessage(token: String?) = RemoteMessage(
        type = RemoteMessageType.HELLO,
        client = RemoteClientInfo(
            id = clientId,
            name = if (isIos) "Nuvio iOS" else "Nuvio Android",
            platform = getPlatform().name,
        ),
        token = token,
    )

    private fun savePairedTvs(tvs: List<PairedTv>) {
        _pairedTvs.value = tvs
        TvRemoteStorage.savePairedTvsJson(tvRemoteJson.encodeToString(pairedListSerializer, tvs))
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun newId(): String = Uuid.random().toString()

    internal fun now(): Long = Clock.System.now().toEpochMilliseconds()

    private const val PAIRING_CONNECT_TIMEOUT_MS = 8_000L
    private const val PARTY_START_TIMEOUT_MS = 15_000L
    private const val PLAY_CONNECT_TIMEOUT_MS = 8_000L
    // ponytail: fixed guess for TV boot + daemon start; raise if slow TVs time out.
    private const val WAKE_CONNECT_TIMEOUT_MS = 60_000L
    private const val WAKE_RESEND_MS = 1_000L
    private const val WAKE_MIN_PACKETS = 3
    private const val BRIEF_DISCOVERY_MS = 10_000L
    private const val BRIEF_DISCOVERY_COOLDOWN_MS = 60_000L
    private const val MAX_VOLUME = 100
    private const val SSAP_KEY_ID = "ssap-key"
    private const val SSAP_KEY_TIMEOUT_MS = 95_000L
    private const val VOLUME_COALESCE_MS = 150L
    private const val LOCAL_VOLUME_HOLD_MS = 1_200L
}

private val tvRemoteJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

@kotlinx.serialization.Serializable
private data class TvRemoteSyncPayload(
    val enabled: Boolean = false,
    val activeTvId: String? = null,
    val pairedTvs: List<PairedTv> = emptyList(),
)
