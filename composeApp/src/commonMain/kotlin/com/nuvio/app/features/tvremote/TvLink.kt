package com.nuvio.app.features.tvremote

import com.nuvio.app.features.tvremote.protocol.RemoteMessage
import com.nuvio.app.features.tvremote.protocol.remoteProtocolJson
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal enum class TvLinkStatus { Connecting, Open, Closed }

/** One logical connection to a TV. Reconnects by itself until [close] is called. */
internal interface TvLink {
    val status: StateFlow<TvLinkStatus>
    val messages: SharedFlow<RemoteMessage>
    /** Sends if the socket is open; commands are never queued across reconnects. */
    fun send(message: RemoteMessage): Boolean
    fun close()
}

internal class WebSocketTvLink(
    private val host: String,
    private val port: Int,
    parentScope: CoroutineScope,
) : TvLink {
    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]))
    private val pendingFrames = Channel<String>(capacity = 32, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val _status = MutableStateFlow(TvLinkStatus.Connecting)
    private val _messages = MutableSharedFlow<RemoteMessage>(extraBufferCapacity = 64)

    override val status: StateFlow<TvLinkStatus> = _status.asStateFlow()
    override val messages: SharedFlow<RemoteMessage> = _messages.asSharedFlow()

    init {
        scope.launch { runLoop() }
    }

    private suspend fun runLoop() {
        var attempt = 0
        while (scope.isActive) {
            _status.value = TvLinkStatus.Connecting
            try {
                client.webSocket(host = host, port = port, path = "/ws") {
                    attempt = 0
                    while (pendingFrames.tryReceive().isSuccess) continue
                    TvRemoteLog.i("ws open $host:$port")
                    _status.value = TvLinkStatus.Open
                    val writer = launch {
                        for (text in pendingFrames) send(Frame.Text(text))
                    }
                    try {
                        for (frame in incoming) {
                            if (frame !is Frame.Text) continue
                            val text = frame.readText()
                            val message = runCatching {
                                remoteProtocolJson.decodeFromString(RemoteMessage.serializer(), text)
                            }.getOrNull()
                            if (message == null) {
                                TvRemoteLog.w("ws bad frame: ${text.take(200)}")
                            } else {
                                _messages.emit(message)
                            }
                        }
                    } finally {
                        writer.cancel()
                    }
                }
                TvRemoteLog.i("ws closed $host:$port")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (attempt == 0) TvRemoteLog.w("ws $host:$port failed", error)
            }
            _status.value = TvLinkStatus.Closed
            attempt += 1
            delay(RECONNECT_DELAYS_MS[(attempt - 1).coerceAtMost(RECONNECT_DELAYS_MS.lastIndex)])
        }
    }

    override fun send(message: RemoteMessage): Boolean {
        if (_status.value != TvLinkStatus.Open) return false
        val text = remoteProtocolJson.encodeToString(RemoteMessage.serializer(), message)
        return pendingFrames.trySend(text).isSuccess
    }

    override fun close() {
        scope.cancel()
        _status.value = TvLinkStatus.Closed
    }

    private companion object {
        val RECONNECT_DELAYS_MS = longArrayOf(1_000, 2_000, 4_000, 8_000, 15_000)

        val client: HttpClient by lazy {
            HttpClient {
                install(WebSockets) {
                    pingIntervalMillis = 10_000
                }
            }
        }
    }
}
