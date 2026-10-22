package com.nuvio.app.features.tvremote.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Wire types for docs/remote-protocol.md. Every message is one flat envelope so the
// JS side on the TV can mirror it without a polymorphic serializer.

const val REMOTE_PROTOCOL_VERSION = 1

object RemoteMessageType {
    const val HELLO = "hello"
    const val PAIR_BEGIN = "pair.begin"
    const val PAIR_CONFIRM = "pair.confirm"
    const val PLAY = "play"
    const val CMD = "cmd"
    const val STATE_GET = "state.get"
    const val PING = "ping"
    /** Ask the TV to invite a friend (`friendCode`) to the watch party it is in. */
    const val PARTY_INVITE = "party.invite"
    /** Remote-only key for the TV's own API (SSAP), in `key`; `refresh` asks for a new one. */
    const val SSAP_KEY = "ssap.key"

    const val WELCOME = "welcome"
    const val PAIR_OK = "pair.ok"
    const val STATE = "state"
    const val ERROR = "error"
    const val PONG = "pong"
    /** The TV's watch party in `party`, null when it is in none. */
    const val PARTY_STATE = "party.state"
    /** Answer to [PARTY_INVITE]: `ok`, `friendCode`, failure reason in `code`. */
    const val PARTY_INVITE_RESULT = "party.invite.result"
}

object RemoteCommand {
    const val PLAY = "play"
    const val PAUSE = "pause"
    const val TOGGLE = "toggle"
    const val STOP = "stop"
    const val SEEK = "seek"
    const val SEEK_BY = "seekBy"
    const val NEXT = "next"
    const val AUDIO = "audio"
    const val SUBTITLE = "subtitle"
    const val SUBTITLE_DELAY = "subtitleDelay"
    const val SKIP = "skip"

    /** Playback rate in `rate`; the TV ignores rates its player does not support. */
    const val SPEED = "speed"

    /** Subtitle appearance in `style`; only the fields set are changed. Works without playback. */
    const val SUBTITLE_STYLE = "subtitleStyle"
}

/** Buttons of the TV's own remote; sent to the TV API directly by [com.nuvio.app.features.tvremote.TvApi]. */
object RemoteKey {
    const val UP = "up"
    const val DOWN = "down"
    const val LEFT = "left"
    const val RIGHT = "right"
    const val OK = "ok"
    const val BACK = "back"
    const val PLAY = "play"
    const val PAUSE = "pause"
    const val REWIND = "rewind"
    const val FAST_FORWARD = "fastForward"
    const val CHANNEL_UP = "channelUp"
    const val CHANNEL_DOWN = "channelDown"
    const val INFO = "info"
    const val POWER = "power"
}

object RemoteLaunchTarget {
    const val HOME = "home"
    const val SETTINGS = "settings"
    fun hdmi(index: Int) = "hdmi$index"
}

@Serializable
data class RemoteTvApp(
    val id: String,
    val title: String,
    /** Icon URL served by the TV itself (self-signed HTTPS, load via TvApi). */
    val icon: String? = null,
)

/** Every field is optional so the phone can change one setting at a time. */
@Serializable
data class RemoteSubtitleStyle(
    val fontSize: Int? = null,
    val textColor: String? = null,
    val textOpacity: Int? = null,
    val bold: Boolean? = null,
    val outlineEnabled: Boolean? = null,
    val outlineColor: String? = null,
    val verticalOffset: Int? = null,
)

object RemoteErrorCode {
    const val NOT_PAIRED = "not_paired"
    const val BAD_CODE = "bad_code"
    const val UNKNOWN_TYPE = "unknown_type"
    const val PLAYBACK_FAILED = "playback_failed"
}

@Serializable
data class RemoteMessage(
    val v: Int = REMOTE_PROTOCOL_VERSION,
    val type: String,
    val id: String? = null,
    // hello / welcome / pairing
    val client: RemoteClientInfo? = null,
    val token: String? = null,
    val tv: RemoteTvInfo? = null,
    val paired: Boolean? = null,
    val protocol: Int? = null,
    val code: String? = null,
    // play / cmd
    val request: RemotePlayRequest? = null,
    val action: String? = null,
    val positionMs: Long? = null,
    val deltaMs: Long? = null,
    val trackId: String? = null,
    val delayMs: Long? = null,
    val rate: Float? = null,
    val style: RemoteSubtitleStyle? = null,
    // TV API key (ssap.key)
    val key: String? = null,
    val refresh: Boolean? = null,
    val port: Int? = null,
    val recentApps: List<String>? = null,
    // state
    val playback: RemotePlaybackState? = null,
    // watch party
    val party: RemoteParty? = null,
    val friendCode: String? = null,
    val ok: Boolean? = null,
    // error / ping
    val message: String? = null,
    val t: Long? = null,
    val serverTime: Long? = null,
)

@Serializable
data class RemoteClientInfo(
    val id: String,
    val name: String,
    val platform: String,
    val appVersion: String? = null,
)

@Serializable
data class RemoteTvInfo(
    val id: String,
    val name: String,
    val model: String? = null,
    val platform: String = "webos",
    /** Network MAC, so the phone can switch the TV on with Wake-on-LAN. */
    val mac: String? = null,
)

@Serializable
data class RemoteMedia(
    // Defaults keep a state message usable when the TV knows less (e.g. playback started on the TV).
    val type: String = "movie",
    val metaId: String = "",
    val metaType: String = "",
    val videoId: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val title: String = "",
    val episodeTitle: String? = null,
    val poster: String? = null,
    val background: String? = null,
    val logo: String? = null,
    // Lets the TV recognise anime for its fixed anime speed without its own metadata.
    val genres: List<String>? = null,
    val country: String? = null,
    val language: String? = null,
)

@Serializable
data class RemoteSubtitle(
    val id: String,
    val url: String,
    val lang: String,
    val label: String? = null,
    val headers: Map<String, String> = emptyMap(),
)

@Serializable
data class RemoteStream(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val responseHeaders: Map<String, String> = emptyMap(),
    val name: String? = null,
    val description: String? = null,
    val addonName: String? = null,
    val addonId: String? = null,
    val filename: String? = null,
    val videoSize: Long? = null,
    val bingeGroup: String? = null,
    val streamType: String? = null,
    val subtitles: List<RemoteSubtitle> = emptyList(),
)

@Serializable
data class RemotePlayRequest(
    val media: RemoteMedia,
    val stream: RemoteStream,
    val startPositionMs: Long = 0L,
)

@Serializable
enum class RemotePlaybackStatus {
    @SerialName("idle") Idle,
    @SerialName("loading") Loading,
    @SerialName("playing") Playing,
    @SerialName("paused") Paused,
    @SerialName("buffering") Buffering,
    @SerialName("ended") Ended,
    @SerialName("error") Error,
}

@Serializable
enum class RemoteSubtitleSource {
    @SerialName("embedded") Embedded,
    @SerialName("addon") Addon,
    @SerialName("external") External,
}

@Serializable
data class RemoteTrack(
    val id: String,
    val label: String,
    val lang: String? = null,
    val selected: Boolean = false,
    val source: RemoteSubtitleSource? = null,
)

@Serializable
enum class RemoteSkipKind {
    @SerialName("intro") Intro,
    @SerialName("recap") Recap,
    @SerialName("outro") Outro,
    @SerialName("credits") Credits,
}

/** The segment the TV's skip button would jump over right now. */
@Serializable
data class RemoteSkipSegment(
    val kind: RemoteSkipKind = RemoteSkipKind.Intro,
    val postCredits: Boolean = false,
    val endMs: Long = 0L,
)

@Serializable
data class RemotePlaybackState(
    val status: RemotePlaybackStatus = RemotePlaybackStatus.Idle,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val at: Long = 0L,
    val rate: Float = 1f,
    val media: RemoteMedia? = null,
    val audioTracks: List<RemoteTrack> = emptyList(),
    val subtitleTracks: List<RemoteTrack> = emptyList(),
    val subtitleDelayMs: Long = 0L,
    val subtitleStyle: RemoteSubtitleStyle? = null,
    val hasNext: Boolean = false,
    val skip: RemoteSkipSegment? = null,
    val error: String? = null,
)

/** The watch party the TV is in, as shown in the remote. */
@Serializable
data class RemoteParty(
    val code: String,
    val title: String = "",
    val season: Int? = null,
    val episode: Int? = null,
    val poster: String? = null,
    val maxMembers: Int = 0,
    /** Whether the TV hosts the party. */
    val isHost: Boolean = false,
    val members: List<RemotePartyMember> = emptyList(),
)

@Serializable
data class RemotePartyMember(
    val name: String = "",
    val host: Boolean = false,
    /** The TV itself. */
    val self: Boolean = false,
)

val remoteProtocolJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
    coerceInputValues = true
}
