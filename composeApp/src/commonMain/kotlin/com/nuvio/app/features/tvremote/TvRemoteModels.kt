package com.nuvio.app.features.tvremote

import com.nuvio.app.features.tvremote.protocol.RemoteParty
import com.nuvio.app.features.tvremote.protocol.RemotePlaybackState
import com.nuvio.app.features.tvremote.protocol.RemotePlaybackStatus
import kotlinx.serialization.Serializable

const val TV_REMOTE_DEFAULT_PORT = 47800

/** A TV the phone has paired with. Persisted as JSON in [TvRemoteStorage]. */
@Serializable
data class PairedTv(
    val id: String,
    val name: String,
    val host: String,
    val port: Int = TV_REMOTE_DEFAULT_PORT,
    val token: String,
    val model: String? = null,
    val lastSeenAtMs: Long = 0L,
    val demo: Boolean = false,
    /** From `welcome`; when set, [TvRemoteRepository.play] wakes the TV with Wake-on-LAN. */
    val mac: String? = null,
    /** Remote-only key for the TV's own API, shared by the daemon (see [TvApi]). */
    val tvApiKey: String? = null,
)

/** A TV announced on the LAN (mDNS / beacon) that may or may not be paired yet. */
data class DiscoveredTv(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
)

enum class TvConnectionStatus {
    Disconnected,
    Connecting,
    Connected,
    Unauthorized,
    Unreachable,
}

data class TvVolumeState(
    val level: Int? = null,
    val muted: Boolean = false,
)

data class TvSessionState(
    val tvId: String? = null,
    val connection: TvConnectionStatus = TvConnectionStatus.Disconnected,
    val playback: RemotePlaybackState = RemotePlaybackState(),
    /** Local clock when [playback] was received, used to interpolate the position. */
    val receivedAtMs: Long = 0L,
    val volume: TvVolumeState = TvVolumeState(),
    val lastError: String? = null,
    /** Watch party the TV is in (protocol `party.state`); null when none or not connected. */
    val party: RemoteParty? = null,
    /** Invites sent through the TV in this session, by friend code. */
    val partyInvites: Map<String, TvPartyInviteStatus> = emptyMap(),
    /** Apps installed on the TV, once requested with [TvRemoteRepository.requestApps]. */
    val apps: List<com.nuvio.app.features.tvremote.protocol.RemoteTvApp> = emptyList(),
) {
    val isPlaybackActive: Boolean
        get() = playback.media != null && playback.status !in setOf(
            RemotePlaybackStatus.Idle,
            RemotePlaybackStatus.Ended,
        )

    /** Position interpolated from the last state while the TV is playing. */
    fun positionAt(nowMs: Long): Long {
        val base = playback.positionMs
        if (playback.status != RemotePlaybackStatus.Playing || receivedAtMs <= 0L) return base
        val elapsed = ((nowMs - receivedAtMs) * playback.rate).toLong().coerceAtLeast(0L)
        val duration = playback.durationMs
        return if (duration > 0L) (base + elapsed).coerceAtMost(duration) else base + elapsed
    }
}

sealed interface TvPartyInviteStatus {
    data object Sending : TvPartyInviteStatus
    data object Sent : TvPartyInviteStatus
    /** [reason] is the TV's code, e.g. `party_full`, `no_party`, `send_failed`, `timeout`. */
    data class Failed(val reason: String) : TvPartyInviteStatus
}

sealed interface TvPairingState {
    data object Idle : TvPairingState
    data object Connecting : TvPairingState
    /** The TV is showing a code; the user has to type it. */
    data class AwaitingCode(val tvName: String, val wrongCode: Boolean = false) : TvPairingState
    data object Verifying : TvPairingState
    data class Paired(val tv: PairedTv) : TvPairingState
    data class Failed(val reason: String) : TvPairingState
}
