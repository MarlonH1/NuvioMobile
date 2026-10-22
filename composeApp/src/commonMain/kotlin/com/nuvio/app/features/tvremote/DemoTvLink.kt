package com.nuvio.app.features.tvremote

import com.nuvio.app.features.tvremote.protocol.RemoteCommand
import com.nuvio.app.features.tvremote.protocol.RemoteErrorCode
import com.nuvio.app.features.tvremote.protocol.RemoteMessage
import com.nuvio.app.features.tvremote.protocol.RemoteMessageType
import com.nuvio.app.features.tvremote.protocol.RemotePlaybackState
import com.nuvio.app.features.tvremote.protocol.RemotePlaybackStatus
import com.nuvio.app.features.tvremote.protocol.RemoteSkipKind
import com.nuvio.app.features.tvremote.protocol.RemoteSkipSegment
import com.nuvio.app.features.tvremote.protocol.RemoteSubtitleSource
import com.nuvio.app.features.tvremote.protocol.RemoteTrack
import com.nuvio.app.features.tvremote.protocol.RemoteTvInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Clock

internal const val DEMO_TV_ID = "demo-tv"
internal const val DEMO_TV_HOST = "demo"
internal const val DEMO_TV_CODE = "123456"
private const val DEMO_TV_TOKEN = "demo-token"
// Demo episodes have an intro from 0:30 to 1:30.
private const val DEMO_INTRO_START_MS = 30_000L
private const val DEMO_INTRO_END_MS = 90_000L

/**
 * Simulated TV that speaks the real protocol, so the remote UI can be tried without a TV.
 * Pairing code is always [DEMO_TV_CODE].
 */
internal class DemoTvLink(parentScope: CoroutineScope) : TvLink {
    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]))
    private val _status = MutableStateFlow(TvLinkStatus.Connecting)
    private val _messages = MutableSharedFlow<RemoteMessage>(extraBufferCapacity = 64)
    private var paired = false
    private var playback = RemotePlaybackState()
    private var ticker: Job? = null

    override val status: StateFlow<TvLinkStatus> = _status.asStateFlow()
    override val messages: SharedFlow<RemoteMessage> = _messages.asSharedFlow()

    init {
        scope.launch {
            delay(400)
            _status.value = TvLinkStatus.Open
        }
    }

    override fun send(message: RemoteMessage): Boolean {
        if (_status.value != TvLinkStatus.Open) return false
        scope.launch {
            delay(60)
            handle(message)
        }
        return true
    }

    override fun close() {
        scope.cancel()
        _status.value = TvLinkStatus.Closed
    }

    private suspend fun reply(message: RemoteMessage) = _messages.emit(message)

    private suspend fun handle(message: RemoteMessage) {
        when (message.type) {
            RemoteMessageType.HELLO -> {
                paired = message.token == DEMO_TV_TOKEN
                reply(
                    RemoteMessage(
                        type = RemoteMessageType.WELCOME,
                        tv = RemoteTvInfo(id = DEMO_TV_ID, name = "Demo-TV", model = "Simulation"),
                        paired = paired,
                        protocol = 1,
                    ),
                )
            }
            RemoteMessageType.PAIR_BEGIN -> TvRemoteLog.i("demo: TV zeigt Code $DEMO_TV_CODE")
            RemoteMessageType.PAIR_CONFIRM -> {
                if (message.code == DEMO_TV_CODE) {
                    paired = true
                    reply(RemoteMessage(type = RemoteMessageType.PAIR_OK, token = DEMO_TV_TOKEN))
                } else {
                    reply(RemoteMessage(type = RemoteMessageType.ERROR, code = RemoteErrorCode.BAD_CODE))
                }
            }
            RemoteMessageType.PING -> reply(
                RemoteMessage(type = RemoteMessageType.PONG, t = message.t, serverTime = now()),
            )
            else -> {
                if (!paired) {
                    reply(RemoteMessage(type = RemoteMessageType.ERROR, code = RemoteErrorCode.NOT_PAIRED))
                    return
                }
                handlePaired(message)
            }
        }
    }

    private suspend fun handlePaired(message: RemoteMessage) {
        when (message.type) {
            RemoteMessageType.STATE_GET -> publishState()
            RemoteMessageType.PLAY -> {
                val request = message.request ?: return
                val hasEpisodes = request.media.season != null
                playback = RemotePlaybackState(
                    status = RemotePlaybackStatus.Loading,
                    positionMs = request.startPositionMs,
                    durationMs = if (hasEpisodes) 52 * 60_000L else 128 * 60_000L,
                    media = request.media,
                    audioTracks = listOf(
                        RemoteTrack(id = "a0", label = "English 5.1", lang = "eng", selected = true),
                        RemoteTrack(id = "a1", label = "Deutsch 5.1", lang = "ger"),
                        RemoteTrack(id = "a2", label = "Commentary", lang = "eng"),
                    ),
                    subtitleTracks = listOf(
                        RemoteTrack(id = "s0", label = "English", lang = "eng", source = RemoteSubtitleSource.Embedded),
                        RemoteTrack(id = "s1", label = "English (SDH)", lang = "eng", source = RemoteSubtitleSource.Embedded),
                    ) + request.stream.subtitles.map { subtitle ->
                        RemoteTrack(
                            id = subtitle.id,
                            label = subtitle.label ?: subtitle.lang,
                            lang = subtitle.lang,
                            source = RemoteSubtitleSource.Addon,
                        )
                    } + RemoteTrack(id = "s9", label = "Deutsch", lang = "ger", source = RemoteSubtitleSource.Addon),
                    hasNext = hasEpisodes,
                )
                publishState()
                delay(1_200)
                updatePlayback { it.copy(status = RemotePlaybackStatus.Playing) }
                startTicker()
            }
            RemoteMessageType.CMD -> handleCommand(message)
        }
    }

    private suspend fun handleCommand(message: RemoteMessage) {
        when (message.action) {
            RemoteCommand.PLAY -> updatePlayback { it.copy(status = RemotePlaybackStatus.Playing) }
            RemoteCommand.PAUSE -> updatePlayback { it.copy(status = RemotePlaybackStatus.Paused) }
            RemoteCommand.TOGGLE -> updatePlayback {
                it.copy(
                    status = if (it.status == RemotePlaybackStatus.Playing) {
                        RemotePlaybackStatus.Paused
                    } else {
                        RemotePlaybackStatus.Playing
                    },
                )
            }
            RemoteCommand.STOP -> {
                ticker?.cancel()
                playback = RemotePlaybackState()
                publishState()
            }
            RemoteCommand.SEEK -> seekTo(message.positionMs ?: return)
            RemoteCommand.SEEK_BY -> seekTo(playback.positionMs + (message.deltaMs ?: return))
            RemoteCommand.NEXT -> {
                val media = playback.media ?: return
                val nextEpisode = (media.episode ?: return) + 1
                updatePlayback {
                    it.copy(
                        status = RemotePlaybackStatus.Loading,
                        positionMs = 0L,
                        media = media.copy(episode = nextEpisode, episodeTitle = "Episode $nextEpisode"),
                    )
                }
                delay(1_000)
                updatePlayback { it.copy(status = RemotePlaybackStatus.Playing) }
            }
            RemoteCommand.AUDIO -> updatePlayback { state ->
                state.copy(audioTracks = state.audioTracks.map { it.copy(selected = it.id == message.trackId) })
            }
            RemoteCommand.SUBTITLE -> updatePlayback { state ->
                state.copy(subtitleTracks = state.subtitleTracks.map { it.copy(selected = it.id == message.trackId) })
            }
            RemoteCommand.SUBTITLE_DELAY -> updatePlayback { it.copy(subtitleDelayMs = message.delayMs ?: 0L) }
            RemoteCommand.SKIP -> seekTo(playback.skip?.endMs ?: return)
            RemoteCommand.SPEED -> updatePlayback { it.copy(rate = message.rate ?: 1f) }
        }
    }

    private suspend fun seekTo(positionMs: Long) {
        val target = positionMs.coerceIn(0L, playback.durationMs)
        val wasPlaying = playback.status == RemotePlaybackStatus.Playing
        updatePlayback { it.copy(positionMs = target, status = RemotePlaybackStatus.Buffering) }
        delay(500)
        updatePlayback {
            it.copy(status = if (wasPlaying) RemotePlaybackStatus.Playing else RemotePlaybackStatus.Paused)
        }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            var sinceLastPublish = 0
            while (isActive) {
                delay(1_000)
                if (playback.status != RemotePlaybackStatus.Playing) continue
                val next = playback.positionMs + 1_000
                if (next >= playback.durationMs) {
                    updatePlayback { it.copy(status = RemotePlaybackStatus.Ended, positionMs = it.durationMs) }
                    continue
                }
                val skipBefore = playback.skip
                playback = withSkip(playback.copy(positionMs = next, at = now()))
                sinceLastPublish += 1
                if (sinceLastPublish >= 5 || playback.skip != skipBefore) {
                    sinceLastPublish = 0
                    publishState()
                }
            }
        }
    }

    private suspend fun updatePlayback(transform: (RemotePlaybackState) -> RemotePlaybackState) {
        playback = withSkip(transform(playback))
        publishState()
    }

    private fun withSkip(state: RemotePlaybackState): RemotePlaybackState {
        val inIntro = state.media?.season != null && state.positionMs in DEMO_INTRO_START_MS until DEMO_INTRO_END_MS
        return state.copy(
            skip = if (inIntro) RemoteSkipSegment(kind = RemoteSkipKind.Intro, endMs = DEMO_INTRO_END_MS) else null,
        )
    }

    private suspend fun publishState() {
        playback = playback.copy(at = now())
        reply(RemoteMessage(type = RemoteMessageType.STATE, playback = playback))
    }

    private fun now(): Long = Clock.System.now().toEpochMilliseconds()
}
