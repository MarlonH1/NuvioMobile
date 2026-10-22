package com.nuvio.app.features.tvremote

import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import io.ktor.http.isSuccess
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** An app on the TV's home screen. */
data class TvLaunchPoint(val id: String, val title: String, val iconUrl: String?)

/**
 * Talks to the TV's own remote API (LG SSAP, wss://<tv>:3001) with the remote-only key that the
 * Nuvio daemon registered and shares with paired phones. Buttons, apps, inputs and power go here
 * directly; Nuvio playback stays on the daemon link.
 */
object TvApi {
    private const val PORT = 3001
    private const val REQUEST_TIMEOUT_MS = 8_000L
    private const val VOLUME_SUBSCRIPTION_ID = "volume"

    // Must match what the daemon registered (remote/daemon/lib/ssap.js).
    private val REMOTE_PERMISSIONS = listOf(
        "LAUNCH", "CONTROL_AUDIO", "CONTROL_POWER", "CONTROL_INPUT_JOYSTICK", "CONTROL_INPUT_MEDIA_PLAYBACK",
        "CONTROL_INPUT_TV", "CONTROL_MOUSE_AND_KEYBOARD", "READ_INSTALLED_APPS", "READ_INPUT_DEVICE_LIST",
        "READ_RUNNING_APPS", "READ_POWER_STATE",
    )

    /** Our key names to the TV pointer socket's button names. */
    private val BUTTONS = mapOf(
        "up" to "UP", "down" to "DOWN", "left" to "LEFT", "right" to "RIGHT", "ok" to "ENTER",
        "back" to "BACK", "home" to "HOME", "play" to "PLAY", "pause" to "PAUSE", "rewind" to "REWIND",
        "fastForward" to "FASTFORWARD", "channelUp" to "CHANNELUP", "channelDown" to "CHANNELDOWN",
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val http by lazy { createTvApiHttpClient() }
    private val mutex = Mutex()
    private var session: DefaultClientWebSocketSession? = null
    private var pointer: DefaultClientWebSocketSession? = null
    private var sessionHost: String? = null
    private var nextId = 0
    private val pending = mutableMapOf<String, CompletableDeferred<JsonObject>>()
    private val iconCache = mutableMapOf<String, ByteArray>()

    /** Presses a remote button; power, home screen apps and inputs use their own requests. */
    suspend fun pressKey(key: String): Boolean {
        if (key == "power") return request("ssap://system/turnOff") != null
        val button = BUTTONS[key] ?: return false
        return sendPointer("type:button\nname:$button\n\n")
    }

    suspend fun volumeUp(): Boolean = request("ssap://audio/volumeUp") != null

    suspend fun volumeDown(): Boolean = request("ssap://audio/volumeDown") != null

    suspend fun setMute(muted: Boolean): Boolean =
        request("ssap://audio/setMute", buildJsonObject { put("mute", muted) }) != null

    suspend fun setVolume(level: Int): Boolean =
        request("ssap://audio/setVolume", buildJsonObject { put("volume", level) }) != null

    private var volumeListener: ((Int?, Boolean) -> Unit)? = null

    /** Reports the TV's volume now and on every change (also from the physical remote). */
    suspend fun watchVolume(onChange: (level: Int?, muted: Boolean) -> Unit) {
        volumeListener = onChange
        connected()?.let { subscribeVolume(it) }
    }

    private suspend fun subscribeVolume(ws: DefaultClientWebSocketSession) {
        if (volumeListener == null) return
        val message = buildJsonObject {
            put("type", "subscribe")
            put("id", VOLUME_SUBSCRIPTION_ID)
            put("uri", "ssap://audio/getVolume")
            put("payload", JsonObject(emptyMap()))
        }
        runCatching { ws.send(Frame.Text(message.toString())) }
    }

    private fun onVolume(payload: JsonObject?) {
        payload ?: return
        // webOS 5+ nests it in volumeStatus; older firmware reports volume/muted at the top.
        val status = payload["volumeStatus"] as? JsonObject ?: payload
        val level = status["volume"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
        val muted = (status["muteStatus"] ?: status["muted"])?.jsonPrimitive?.booleanOrNull ?: false
        volumeListener?.invoke(level, muted)
    }

    suspend fun launch(appId: String): Boolean =
        request("ssap://system.launcher/launch", buildJsonObject { put("id", appId) }) != null

    suspend fun launchPoints(): List<TvLaunchPoint> {
        val payload = request("ssap://com.webos.applicationManager/listLaunchPoints") ?: return emptyList()
        val points = payload["launchPoints"] as? JsonArray ?: return emptyList()
        return points.mapNotNull { element ->
            val point = element as? JsonObject ?: return@mapNotNull null
            if (point["hidden"]?.jsonPrimitive?.booleanOrNull == true) return@mapNotNull null
            val id = point["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val title = point["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            TvLaunchPoint(id, title, point["icon"]?.jsonPrimitive?.contentOrNull?.takeIf { it.startsWith("http") })
        }.distinctBy { it.id }
    }

    /** Icon bytes; the TV serves them with its self-signed certificate, so loaded through this client. */
    suspend fun iconBytes(url: String): ByteArray? {
        iconCache[url]?.let { return it }
        val bytes = runCatching {
            val response = http.get(url)
            if (response.status.isSuccess()) response.readRawBytes() else null
        }.getOrNull() ?: return null
        iconCache[url] = bytes
        return bytes
    }

    private suspend fun request(uri: String, payload: JsonObject = JsonObject(emptyMap())): JsonObject? {
        val first = send(uri, payload)
        if (first != null || !lastRequestUnauthorized) return first
        // The key was revoked on the TV: get a fresh one from the daemon once and retry.
        disconnect()
        if (TvRemoteRepository.tvApiKey(refresh = true) == null) return null
        return send(uri, payload)
    }

    private var lastRequestUnauthorized = false

    private suspend fun send(uri: String, payload: JsonObject): JsonObject? {
        val ws = connected() ?: return null
        val id = mutex.withLock { "r${nextId++}" }
        val answer = CompletableDeferred<JsonObject>()
        mutex.withLock { pending[id] = answer }
        val message = buildJsonObject {
            put("type", "request")
            put("id", id)
            put("uri", uri)
            put("payload", payload)
        }
        if (runCatching { ws.send(Frame.Text(message.toString())) }.isFailure) {
            disconnect()
            return null
        }
        val reply = withTimeoutOrNull(REQUEST_TIMEOUT_MS) { answer.await() }
        mutex.withLock { pending.remove(id) }
        lastRequestUnauthorized = reply?.get("error")?.jsonPrimitive?.contentOrNull?.startsWith("401") == true
        if (reply?.get("type")?.jsonPrimitive?.contentOrNull != "response") return null
        return reply["payload"] as? JsonObject ?: JsonObject(emptyMap())
    }

    private suspend fun sendPointer(text: String): Boolean {
        val socket = pointer?.takeIf { it.isActiveSession() } ?: openPointer() ?: return false
        return runCatching { socket.send(Frame.Text(text)) }.isSuccess
    }

    private suspend fun openPointer(): DefaultClientWebSocketSession? {
        val payload = request("ssap://com.webos.service.networkinput/getPointerInputSocket") ?: return null
        val path = payload["socketPath"]?.jsonPrimitive?.contentOrNull ?: return null
        val socket = runCatching { http.webSocketSession(path) }.getOrNull() ?: return null
        pointer = socket
        return socket
    }

    /** Session registered with the shared key, for the active TV. */
    private suspend fun connected(): DefaultClientWebSocketSession? {
        val tv = TvRemoteRepository.activeTv() ?: return null
        session?.takeIf { it.isActiveSession() && sessionHost == tv.host }?.let { return it }
        disconnect()
        val key = TvRemoteRepository.tvApiKey(refresh = false) ?: return null
        val ws = runCatching { http.webSocketSession("wss://${tv.host}:$PORT/") }.getOrNull() ?: return null
        val registered = CompletableDeferred<Boolean>()
        scope.launch {
            runCatching {
                for (frame in ws.incoming) {
                    val text = (frame as? Frame.Text)?.readText() ?: continue
                    val message = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: continue
                    when (message["type"]?.jsonPrimitive?.contentOrNull) {
                        "registered" -> registered.complete(true)
                        "error" -> if (message["id"]?.jsonPrimitive?.contentOrNull == "register") registered.complete(false)
                    }
                    val id = message["id"]?.jsonPrimitive?.contentOrNull ?: continue
                    if (id == VOLUME_SUBSCRIPTION_ID) {
                        onVolume(message["payload"] as? JsonObject)
                        continue
                    }
                    mutex.withLock { pending[id] }?.complete(message)
                }
            }
            registered.complete(false)
        }
        val register = buildJsonObject {
            put("type", "register")
            put("id", "register")
            put(
                "payload",
                buildJsonObject {
                    put("forcePairing", false)
                    put("pairingType", "PROMPT")
                    put("client-key", key)
                    put(
                        "manifest",
                        buildJsonObject {
                            put("manifestVersion", 1)
                            put("appVersion", "1.0")
                            put("permissions", buildJsonArray { REMOTE_PERMISSIONS.forEach { add(JsonPrimitive(it)) } })
                        },
                    )
                },
            )
        }
        runCatching { ws.send(Frame.Text(register.toString())) }
        if (withTimeoutOrNull(REQUEST_TIMEOUT_MS) { registered.await() } != true) {
            runCatching { ws.close() }
            return null
        }
        session = ws
        sessionHost = tv.host
        // Subscriptions belong to a session: renew the volume one after every reconnect.
        subscribeVolume(ws)
        return ws
    }

    suspend fun disconnect() {
        runCatching { pointer?.close() }
        runCatching { session?.close() }
        pointer = null
        session = null
        sessionHost = null
    }

    private fun DefaultClientWebSocketSession.isActiveSession(): Boolean = !outgoing.isClosedForSend
}
