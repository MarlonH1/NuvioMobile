package com.nuvio.app.features.remotehost

import com.nuvio.app.features.party.PartyRepository
import com.nuvio.app.features.player.PlayerRemoteHandle
import com.nuvio.app.features.player.PlayerScreenRuntime
import com.nuvio.app.features.player.SUBTITLE_DELAY_MAX_MS
import com.nuvio.app.features.player.SUBTITLE_DELAY_MIN_MS
import com.nuvio.app.features.player.currentPlaybackPositionMs
import com.nuvio.app.features.player.desktop.NativePlayerController
import com.nuvio.app.features.player.finishTimelineSeek
import com.nuvio.app.features.player.isPartyControlLocked
import com.nuvio.app.features.player.partyBlocksControl
import com.nuvio.app.features.player.persistAddonSubtitlePreference
import com.nuvio.app.features.player.persistAudioPreference
import com.nuvio.app.features.player.persistInternalSubtitlePreference
import com.nuvio.app.features.player.playNextEpisode
import com.nuvio.app.features.player.publishPartyPlayback
import com.nuvio.app.features.player.seekBy
import com.nuvio.app.features.player.selectPlaybackSpeed
import com.nuvio.app.features.player.selectedAddonSubtitle
import com.nuvio.app.features.player.setSubtitleDelay
import com.nuvio.app.features.player.skip.internalSkipAction
import com.nuvio.app.features.player.skipInterval
import com.nuvio.app.features.player.startParty
import com.nuvio.app.features.player.toggleOwnPlayback
import com.nuvio.app.features.player.visibleAddonSubtitles
import com.nuvio.app.features.tvremote.protocol.RemoteCommand
import com.nuvio.app.features.tvremote.protocol.RemoteMedia
import com.nuvio.app.features.tvremote.protocol.RemoteMessage
import com.nuvio.app.features.tvremote.protocol.RemoteParty
import com.nuvio.app.features.tvremote.protocol.RemotePartyMember
import com.nuvio.app.features.tvremote.protocol.RemotePlaybackState
import com.nuvio.app.features.tvremote.protocol.RemotePlaybackStatus
import com.nuvio.app.features.tvremote.protocol.RemoteSkipKind
import com.nuvio.app.features.tvremote.protocol.RemoteSkipSegment
import com.nuvio.app.features.tvremote.protocol.RemoteSubtitleSource
import com.nuvio.app.features.tvremote.protocol.RemoteTrack

// The desktop player as the phone remote sees it: state out, commands in. Main (Swing) thread only.

private const val EMBEDDED_SUBTITLE_PREFIX = "e:"
private const val ADDON_SUBTITLE_PREFIX = "a:"

/** Player volume and mute for `state`; null without a desktop player. */
internal data class RemoteVolume(val level: Int, val muted: Boolean)

internal fun PlayerRemoteHandle?.remoteVolume(): RemoteVolume? {
    val controller = this?.runtime?.playerController as? NativePlayerController ?: return null
    return RemoteVolume(controller.remoteVolumePercent, controller.remoteMuted)
}

internal fun PlayerRemoteHandle?.remotePlayback(nowMs: Long): RemotePlaybackState {
    val runtime = this?.runtime ?: return RemotePlaybackState(at = nowMs)
    val snapshot = runtime.playbackSnapshot
    val status = when {
        runtime.errorMessage != null -> RemotePlaybackStatus.Error
        snapshot.isEnded -> RemotePlaybackStatus.Ended
        !runtime.initialLoadCompleted -> RemotePlaybackStatus.Loading
        snapshot.isPlaying -> RemotePlaybackStatus.Playing
        snapshot.isLoading && runtime.shouldPlay -> RemotePlaybackStatus.Buffering
        else -> RemotePlaybackStatus.Paused
    }
    return RemotePlaybackState(
        status = status,
        positionMs = snapshot.positionMs.coerceAtLeast(0L),
        durationMs = snapshot.durationMs.coerceAtLeast(0L),
        at = nowMs,
        rate = snapshot.playbackSpeed,
        media = runtime.remoteMedia(),
        audioTracks = runtime.audioTracks.map { track ->
            RemoteTrack(
                id = track.index.toString(),
                label = track.label,
                lang = track.language,
                selected = track.index == runtime.selectedAudioIndex,
            )
        },
        subtitleTracks = runtime.remoteSubtitleTracks(),
        subtitleDelayMs = runtime.subtitleDelayMs.toLong(),
        hasNext = runtime.nextEpisodeInfo?.hasAired == true,
        skip = runtime.remoteSkip(),
        error = runtime.errorMessage,
    )
}

private fun PlayerScreenRuntime.remoteMedia() = RemoteMedia(
    type = if (isSeries || activeSeasonNumber != null) "series" else contentType ?: "movie",
    metaId = parentMetaId,
    metaType = parentMetaType,
    videoId = activeVideoId,
    season = activeSeasonNumber,
    episode = activeEpisodeNumber,
    title = title,
    episodeTitle = activeEpisodeTitle,
    poster = poster,
    background = background,
    logo = logo,
    genres = playerMeta?.genres?.takeIf { it.isNotEmpty() },
)

private fun PlayerScreenRuntime.remoteSubtitleTracks(): List<RemoteTrack> {
    val embedded = subtitleTracks.map { track ->
        RemoteTrack(
            id = EMBEDDED_SUBTITLE_PREFIX + track.index,
            label = track.label,
            lang = track.language,
            selected = !useCustomSubtitles && track.index == selectedSubtitleIndex,
            source = RemoteSubtitleSource.Embedded,
        )
    }
    val selectedAddonId = selectedAddonSubtitle?.id?.takeIf { useCustomSubtitles }
    val addons = visibleAddonSubtitles.map { subtitle ->
        RemoteTrack(
            id = ADDON_SUBTITLE_PREFIX + subtitle.id,
            label = listOfNotNull(subtitle.display, subtitle.addonName).distinct().joinToString(" · "),
            lang = subtitle.language,
            selected = subtitle.id == selectedAddonId,
            source = RemoteSubtitleSource.Addon,
        )
    }
    return embedded + addons
}

private fun PlayerScreenRuntime.remoteSkip(): RemoteSkipSegment? {
    val interval = activeSkipInterval?.takeUnless { skipIntervalDismissed } ?: return null
    val type = interval.type.lowercase()
    val kind = when {
        "recap" in type -> RemoteSkipKind.Recap
        "credit" in type -> RemoteSkipKind.Credits
        "outro" in type || type == "ed" || "ending" in type -> RemoteSkipKind.Outro
        else -> RemoteSkipKind.Intro
    }
    return RemoteSkipSegment(
        kind = kind,
        postCredits = interval.internalSkipAction(skipIntervals, playbackSnapshot.durationMs)?.skipsToPostCredits == true,
        endMs = (interval.endTime * 1000).toLong(),
    )
}

/** The account's watch party as `party.state` shows it on the phone. */
internal fun remoteParty(): RemoteParty? {
    val party = PartyRepository.state.value.party ?: return null
    return RemoteParty(
        code = party.code,
        title = party.media.title,
        season = party.media.season,
        episode = party.media.episode,
        poster = party.media.poster,
        maxMembers = party.maxMembers,
        isHost = party.isHost,
        members = party.members.map { member ->
            RemotePartyMember(
                name = member.displayName,
                host = member.id == party.hostMemberId,
                self = member.id == party.memberId,
            )
        },
    )
}

/** Runs one `cmd` from the phone; false when there is nothing to apply it to. */
internal fun PlayerRemoteHandle?.applyRemoteCommand(message: RemoteMessage): Boolean {
    // Party commands also work without an open player.
    when (message.action) {
        RemoteCommand.PARTY_END -> {
            val party = PartyRepository.state.value.party ?: return false
            if (party.isHost) PartyRepository.end() else PartyRepository.leave()
            return true
        }
        RemoteCommand.PARTY_START -> {
            this?.runtime?.startParty() ?: return false
            return true
        }
    }
    val handle = this ?: return false
    val runtime = handle.runtime
    when (message.action) {
        RemoteCommand.PLAY -> runtime.remoteSetPlaying(true)
        RemoteCommand.PAUSE -> runtime.remoteSetPlaying(false)
        RemoteCommand.TOGGLE -> runtime.remoteSetPlaying(!runtime.playbackSnapshot.isPlaying)
        RemoteCommand.STOP -> handle.close()
        RemoteCommand.SEEK -> runtime.finishTimelineSeek((message.positionMs ?: return false).coerceAtLeast(0L))
        RemoteCommand.SEEK_BY -> runtime.seekBy(message.deltaMs ?: return false, revealControls = false)
        RemoteCommand.NEXT -> {
            if (runtime.nextEpisodeInfo?.hasAired != true || runtime.partyBlocksControl()) return false
            runtime.playNextEpisode()
        }
        RemoteCommand.AUDIO -> {
            val index = message.trackId?.toIntOrNull() ?: return false
            runtime.selectedAudioIndex = index
            runtime.persistAudioPreference(runtime.audioTracks.firstOrNull { it.index == index })
            runtime.playerController?.selectAudioTrack(index)
        }
        RemoteCommand.SUBTITLE -> runtime.remoteSelectSubtitle(message.trackId)
        RemoteCommand.SUBTITLE_DELAY -> runtime.setSubtitleDelay(
            (message.delayMs ?: 0L).coerceIn(SUBTITLE_DELAY_MIN_MS.toLong(), SUBTITLE_DELAY_MAX_MS.toLong()).toInt(),
        )
        RemoteCommand.SPEED -> runtime.selectPlaybackSpeed(message.rate ?: return false)
        RemoteCommand.SKIP -> runtime.activeSkipInterval?.let { runtime.skipInterval(it) } ?: return false
        RemoteCommand.VOLUME -> {
            val controller = runtime.playerController as? NativePlayerController ?: return false
            message.volume?.let(controller::setRemoteVolume)
            message.muted?.let(controller::setRemoteMuted)
        }
        else -> return false
    }
    return true
}

private fun PlayerScreenRuntime.remoteSetPlaying(play: Boolean) {
    if (isPartyControlLocked) {
        if (play != shouldPlay) toggleOwnPlayback(revealControls = false)
        return
    }
    val restart = play && playbackSnapshot.isEnded
    if (restart) playerController?.seekTo(0L)
    shouldPlay = play
    if (play) playerController?.play() else playerController?.pause()
    publishPartyPlayback(positionMs = if (restart) 0L else currentPlaybackPositionMs())
}

/** Same as picking a subtitle in the player's own menu; null turns subtitles off. */
private fun PlayerScreenRuntime.remoteSelectSubtitle(trackId: String?) {
    val wasCustom = useCustomSubtitles
    when {
        trackId == null -> {
            selectedSubtitleIndex = -1
            selectedAddonSubtitleId = null
            useCustomSubtitles = false
            isUserExplicitSubtitleSelection = true
            persistInternalSubtitlePreference(null)
            if (wasCustom) playerController?.clearExternalSubtitleAndSelect(-1) else playerController?.selectSubtitleTrack(-1)
        }
        trackId.startsWith(EMBEDDED_SUBTITLE_PREFIX) -> {
            val index = trackId.removePrefix(EMBEDDED_SUBTITLE_PREFIX).toIntOrNull() ?: return
            selectedSubtitleIndex = index
            selectedAddonSubtitleId = null
            useCustomSubtitles = false
            isUserExplicitSubtitleSelection = true
            persistInternalSubtitlePreference(subtitleTracks.firstOrNull { it.index == index })
            if (wasCustom) playerController?.clearExternalSubtitleAndSelect(index) else playerController?.selectSubtitleTrack(index)
        }
        trackId.startsWith(ADDON_SUBTITLE_PREFIX) -> {
            val id = trackId.removePrefix(ADDON_SUBTITLE_PREFIX)
            val addon = visibleAddonSubtitles.firstOrNull { it.id == id } ?: return
            selectedAddonSubtitleId = addon.id
            selectedSubtitleIndex = -1
            useCustomSubtitles = true
            isUserExplicitSubtitleSelection = true
            persistAddonSubtitlePreference(addon)
            playerController?.setSubtitleUri(addon.url)
        }
    }
}
