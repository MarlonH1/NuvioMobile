package com.nuvio.app.features.tvremote

import com.nuvio.app.getPlatform
import com.nuvio.app.features.tvremote.protocol.RemoteClientInfo
import com.nuvio.app.features.tvremote.protocol.RemoteCommand
import com.nuvio.app.features.tvremote.protocol.RemoteErrorCode
import com.nuvio.app.features.tvremote.protocol.RemoteMessage
import com.nuvio.app.features.tvremote.protocol.RemoteMessageType
import com.nuvio.app.features.tvremote.protocol.RemotePlayRequest
import com.nuvio.app.features.tvremote.protocol.RemotePlaybackState
import com.nuvio.app.features.tvremote.protocol.RemotePlaybackStatus
import com.nuvio.app.features.tvremote.protocol.RemoteVolumeAction
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

    /** The selected playback target; `null` means "this phone". */
    private val _activeTvId = MutableStateFlow<String?>(null)
    val activeTvId: StateFlow<String?> = _activeTvId.asStateFlow()

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
                    savePairedTvs(_pairedTvs.value.map { if (it.id == tv.id) it.copy(lastSeenAtMs = now()) else it })
                    sessionLink.send(RemoteMessage(type = RemoteMessageType.STATE_GET))
                } else {
                    TvRemoteLog.w("${tv.name} rejected our token, re-pairing needed")
                    _session.update { it.copy(connection = TvConnectionStatus.Unauthorized) }
                }
            }
            RemoteMessageType.STATE -> {
                val playback = message.playback ?: return
                _session.update { it.copy(playback = playback, receivedAtMs = now()) }
            }
            RemoteMessageType.VOLUME_STATE -> _session.update {
                // Right after a local change the TV may still report older levels.
                val level = if (now() < localVolumeUntilMs) it.volume.level else message.level
                it.copy(volume = TvVolumeState(level = level, muted = message.muted ?: false))
            }
            RemoteMessageType.ERROR -> {
                TvRemoteLog.w("tv error ${message.code}: ${message.message.orEmpty()}")
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
            val connected = withTimeoutOrNull(PLAY_CONNECT_TIMEOUT_MS) {
                _session.first { it.connection == TvConnectionStatus.Connected }
            }
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

    fun setSpeed(rate: Float) {
        _session.update { it.copy(playback = it.playback.copy(rate = rate)) }
        command(RemoteCommand.SPEED, rate = rate)
    }

    fun volumeUp() = volume(RemoteVolumeAction.UP)

    fun volumeDown() = volume(RemoteVolumeAction.DOWN)

    fun toggleMute() = volume(if (_session.value.volume.muted) RemoteVolumeAction.UNMUTE else RemoteVolumeAction.MUTE)

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
            val latest = _session.value.volume.level ?: target
            send(RemoteMessage(type = RemoteMessageType.VOLUME, action = RemoteVolumeAction.SET, level = latest))
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

    /** Forces a reconnect, e.g. after the user tapped "retry". */
    fun reconnect() {
        linkTvId = null
        syncLink()
    }

    private fun volume(action: String) {
        send(RemoteMessage(type = RemoteMessageType.VOLUME, action = action))
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
    private const val PLAY_CONNECT_TIMEOUT_MS = 8_000L
    private const val BRIEF_DISCOVERY_MS = 10_000L
    private const val BRIEF_DISCOVERY_COOLDOWN_MS = 60_000L
    private const val MAX_VOLUME = 100
    private const val VOLUME_COALESCE_MS = 150L
    private const val LOCAL_VOLUME_HOLD_MS = 1_200L
}

private val tvRemoteJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
