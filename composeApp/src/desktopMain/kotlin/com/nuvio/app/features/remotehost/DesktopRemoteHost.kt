package com.nuvio.app.features.remotehost

import co.touchlab.kermit.Logger
import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.features.player.PlayerRemoteRegistry
import com.nuvio.app.features.tvremote.TV_REMOTE_DEFAULT_PORT
import com.nuvio.app.features.tvremote.protocol.REMOTE_PROTOCOL_VERSION
import com.nuvio.app.features.tvremote.protocol.RemoteErrorCode
import com.nuvio.app.features.tvremote.protocol.RemoteMessage
import com.nuvio.app.features.tvremote.protocol.RemoteMessageType
import com.nuvio.app.features.tvremote.protocol.RemoteTargetPlatform
import com.nuvio.app.features.tvremote.protocol.RemoteTvInfo
import com.nuvio.app.features.tvremote.protocol.remoteProtocolJson
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/** A phone asking to pair: its name and the code the desktop app shows. */
internal data class RemotePairingPrompt(val clientName: String, val code: String)

/**
 * Makes the desktop app a target of the phone remote, like the TV (docs/remote-protocol.md):
 * WebSocket server on [TV_REMOTE_DEFAULT_PORT] at `/ws`, announced over mDNS as
 * `_nuvio-remote._tcp` with `platform=desktop`. The phone sees what plays here, controls it,
 * and can send streams to play.
 */
internal object DesktopRemoteHost {
    private val log = Logger.withTag("DesktopRemoteHost")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = DesktopStorage.store("nuvio_remote_host")
    private val tokensSerializer = MapSerializer(String.serializer(), String.serializer())
    private val random = SecureRandom()

    private val _pairing = MutableStateFlow<RemotePairingPrompt?>(null)
    /** Shown in the app window while a phone waits for its code to be typed. */
    val pairing: StateFlow<RemotePairingPrompt?> = _pairing.asStateFlow()

    /** Stable id of this computer, also in the mDNS TXT record (lets the app skip itself). */
    val hostId: String by lazy {
        store.getString(KEY_ID) ?: UUID.randomUUID().toString().also { store.putString(KEY_ID, it) }
    }

    private val hostName: String by lazy {
        val machine = runCatching { InetAddress.getLocalHost().hostName }.getOrNull()
            ?.substringBefore('.')
            ?.takeIf { it.isNotBlank() && it != "localhost" }
            ?: System.getenv("HOSTNAME")?.takeIf { it.isNotBlank() }
        if (machine != null) "Nuvio PC ($machine)" else "Nuvio PC"
    }

    /** Pairing tokens to the phone name they were issued to. */
    private val tokens: MutableMap<String, String> by lazy {
        val saved = store.getString(KEY_TOKENS)
            ?.let { runCatching { remoteProtocolJson.decodeFromString(tokensSerializer, it) }.getOrNull() }
            .orEmpty()
        ConcurrentHashMap(saved)
    }

    private val clients = ConcurrentHashMap.newKeySet<Client>()
    private var server: EmbeddedServer<*, *>? = null
    private var jmdns: JmDNS? = null
    private var pairingCode: String? = null
    private var pairingClient: Client? = null
    private var wrongCodes = 0

    private class Client(val name: String) {
        val outgoing = Channel<String>(Channel.BUFFERED)
        @Volatile var paired = false
        @Volatile var clientName = name
        @Volatile var lastStateKey: Any? = null
        @Volatile var lastStateAtMs = 0L
        @Volatile var lastParty: Any? = UNSENT

        fun send(message: RemoteMessage) {
            outgoing.trySend(remoteProtocolJson.encodeToString(RemoteMessage.serializer(), message))
        }
    }

    fun start() {
        if (server != null) return
        scope.launch {
            val started = runCatching {
                embeddedServer(CIO, port = TV_REMOTE_DEFAULT_PORT, host = "0.0.0.0") {
                    install(WebSockets) {
                        pingPeriod = 15.seconds
                        timeout = 30.seconds
                    }
                    routing {
                        webSocket("/ws") {
                            val client = Client(call.request.local.remoteAddress)
                            clients += client
                            val writer = launch {
                                for (text in client.outgoing) send(Frame.Text(text))
                            }
                            try {
                                for (frame in incoming) {
                                    if (frame !is Frame.Text) continue
                                    val message = runCatching {
                                        remoteProtocolJson.decodeFromString(RemoteMessage.serializer(), frame.readText())
                                    }.getOrNull() ?: continue
                                    handle(client, message)
                                }
                            } finally {
                                clients -= client
                                writer.cancel()
                                client.outgoing.close()
                                if (pairingClient === client) cancelPairing()
                            }
                        }
                    }
                }.start(wait = false)
            }.onFailure { log.w { "remote host not started (port $TV_REMOTE_DEFAULT_PORT busy?): ${it.message}" } }
                .getOrNull() ?: return@launch
            server = started
            log.i { "remote host on :$TV_REMOTE_DEFAULT_PORT as \"$hostName\"" }
            announce()
            publishStateLoop()
        }
        Runtime.getRuntime().addShutdownHook(Thread { stop() })
    }

    fun stop() {
        runCatching { jmdns?.unregisterAllServices() }
        runCatching { jmdns?.close() }
        jmdns = null
        runCatching { server?.stop(200, 500) }
        server = null
    }

    /** The user closed the code dialog: the phone's code no longer works. */
    fun cancelPairing() {
        pairingCode = null
        pairingClient = null
        _pairing.value = null
    }

    private fun announce() {
        val address = lanAddress()
        val instance = runCatching { JmDNS.create(address, "nuvio-remote-host") }
            .onFailure { log.w { "mDNS unavailable: ${it.message}" } }
            .getOrNull() ?: return
        val info = ServiceInfo.create(
            SERVICE_TYPE,
            hostName,
            TV_REMOTE_DEFAULT_PORT,
            0,
            0,
            mapOf(
                "v" to REMOTE_PROTOCOL_VERSION.toString(),
                "id" to hostId,
                "name" to hostName,
                "platform" to RemoteTargetPlatform.DESKTOP,
            ),
        )
        runCatching { instance.registerService(info) }
            .onSuccess { log.i { "announced on ${address?.hostAddress}" } }
            .onFailure { log.w { "mDNS announce failed: ${it.message}" } }
        jmdns = instance
    }

    private suspend fun handle(client: Client, message: RemoteMessage) {
        when (message.type) {
            RemoteMessageType.HELLO -> {
                message.client?.name?.let { client.clientName = it }
                val token = message.token
                client.paired = token != null && tokens.containsKey(token)
                client.send(
                    RemoteMessage(
                        type = RemoteMessageType.WELCOME,
                        id = message.id,
                        tv = RemoteTvInfo(
                            id = hostId,
                            name = hostName,
                            model = System.getProperty("os.name"),
                            platform = RemoteTargetPlatform.DESKTOP,
                        ),
                        paired = client.paired,
                        protocol = REMOTE_PROTOCOL_VERSION,
                    ),
                )
                if (client.paired) sendState(client, force = true)
            }
            RemoteMessageType.PAIR_BEGIN -> {
                val code = (0 until 6).joinToString("") { random.nextInt(10).toString() }
                pairingCode = code
                pairingClient = client
                wrongCodes = 0
                _pairing.value = RemotePairingPrompt(client.clientName, code)
                notifyPairing(client.clientName, code)
            }
            RemoteMessageType.PAIR_CONFIRM -> {
                val expected = pairingCode
                if (expected == null || pairingClient !== client || message.code?.trim() != expected) {
                    wrongCodes += 1
                    if (expected != null && wrongCodes >= MAX_WRONG_CODES) {
                        // A new code after too many wrong ones, so it cannot be guessed.
                        handle(client, RemoteMessage(type = RemoteMessageType.PAIR_BEGIN))
                    }
                    client.send(RemoteMessage(type = RemoteMessageType.ERROR, id = message.id, code = RemoteErrorCode.BAD_CODE))
                    return
                }
                val token = UUID.randomUUID().toString()
                tokens[token] = client.clientName
                store.putString(KEY_TOKENS, remoteProtocolJson.encodeToString(tokensSerializer, tokens.toMap()))
                client.paired = true
                cancelPairing()
                client.send(RemoteMessage(type = RemoteMessageType.PAIR_OK, id = message.id, token = token))
                log.i { "paired with ${client.clientName}" }
            }
            RemoteMessageType.PING -> client.send(
                RemoteMessage(type = RemoteMessageType.PONG, id = message.id, t = message.t, serverTime = System.currentTimeMillis()),
            )
            else -> {
                if (!client.paired) {
                    client.send(RemoteMessage(type = RemoteMessageType.ERROR, id = message.id, code = RemoteErrorCode.NOT_PAIRED))
                    return
                }
                when (message.type) {
                    RemoteMessageType.STATE_GET -> sendState(client, force = true)
                    RemoteMessageType.CMD -> {
                        withContext(Dispatchers.Main) { PlayerRemoteRegistry.active.value.applyRemoteCommand(message) }
                        // Show the result right away rather than at the next tick.
                        delay(COMMAND_STATE_DELAY_MS)
                        sendState(client, force = true)
                    }
                    RemoteMessageType.PLAY -> {
                        val request = message.request ?: return
                        log.i { "play from ${client.clientName}: ${request.media.title}" }
                        withContext(Dispatchers.Main) {
                            bringWindowToFront()
                            PlayerRemoteRegistry.requestPlay(request)
                        }
                    }
                    // party.invite, ssap.key (TV API), …: not available on a computer.
                    else -> client.send(
                        RemoteMessage(type = RemoteMessageType.ERROR, id = message.id, code = RemoteErrorCode.UNKNOWN_TYPE),
                    )
                }
            }
        }
    }

    /** Sends state when it changed (position aside) and every few seconds as a heartbeat. */
    private suspend fun publishStateLoop() {
        while (scope.isActive) {
            delay(STATE_TICK_MS)
            if (clients.none { it.paired }) continue
            clients.filter { it.paired }.forEach { sendState(it, force = false) }
        }
    }

    private suspend fun sendState(client: Client, force: Boolean) {
        val now = System.currentTimeMillis()
        val (playback, volume, party) = withContext(Dispatchers.Main) {
            val handle = PlayerRemoteRegistry.active.value
            Triple(handle.remotePlayback(now), handle.remoteVolume(), remoteParty())
        }
        val key = listOf(playback.copy(positionMs = 0L, at = 0L), volume)
        val changed = key != client.lastStateKey
        if (force || changed || now - client.lastStateAtMs >= HEARTBEAT_MS) {
            client.lastStateKey = key
            client.lastStateAtMs = now
            client.send(
                RemoteMessage(
                    type = RemoteMessageType.STATE,
                    playback = playback,
                    volume = volume?.level,
                    muted = volume?.muted,
                    tvPoweredOn = true,
                ),
            )
        }
        if (force || party != client.lastParty) {
            client.lastParty = party
            client.send(RemoteMessage(type = RemoteMessageType.PARTY_STATE, party = party))
        }
    }

    /** The code also as a desktop notification: the player's video can cover the app's dialog. */
    private fun notifyPairing(clientName: String, code: String) {
        if (!System.getProperty("os.name").orEmpty().contains("linux", ignoreCase = true)) return
        runCatching {
            ProcessBuilder("notify-send", "-a", "Nuvio", "-u", "critical", "Pair $clientName", "Code: $code")
                .redirectErrorStream(true)
                .start()
        }
    }

    private fun bringWindowToFront() {
        java.awt.Window.getWindows().firstOrNull { it.isShowing && it is java.awt.Frame }?.let { window ->
            (window as java.awt.Frame).state = java.awt.Frame.NORMAL
            window.toFront()
        }
    }

    // A real LAN IPv4 address rather than loopback, VPN or container bridges.
    private fun lanAddress(): InetAddress? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual && !it.isPointToPoint }
            .filterNot { iface -> listOf("docker", "br-", "veth", "virbr", "tun", "wg").any { iface.name.startsWith(it) } }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
    }.getOrNull()

    private val UNSENT = Any()
    private const val SERVICE_TYPE = "_nuvio-remote._tcp.local."
    private const val KEY_ID = "host_id"
    private const val KEY_TOKENS = "tokens"
    private const val MAX_WRONG_CODES = 5
    private const val STATE_TICK_MS = 500L
    private const val HEARTBEAT_MS = 5_000L
    private const val COMMAND_STATE_DELAY_MS = 250L
}
