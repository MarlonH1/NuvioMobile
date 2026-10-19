package com.nuvio.app.features.tvremote

import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.features.party.isShareableStreamUrl
import com.nuvio.app.features.player.PlayerLaunch
import com.nuvio.app.features.tvremote.protocol.RemoteMedia
import com.nuvio.app.features.tvremote.protocol.RemotePlayRequest
import com.nuvio.app.features.tvremote.protocol.RemoteStream
import com.nuvio.app.features.tvremote.protocol.RemoteSubtitle
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class TvRemoteDestination { Devices, Control }

/** Lets feature code open TV remote screens without threading callbacks through the app. */
object TvRemoteNavigation {
    private val _requests = MutableSharedFlow<TvRemoteDestination>(extraBufferCapacity = 4)
    val requests: SharedFlow<TvRemoteDestination> = _requests.asSharedFlow()

    fun open(destination: TvRemoteDestination) {
        _requests.tryEmit(destination)
    }
}

object TvRemoteCast {
    /** True when a stream picked now would be sent to a TV instead of the local player. */
    fun isCastTargetActive(): Boolean {
        TvRemoteRepository.ensureLoaded()
        return TvRemoteRepository.enabled.value && TvRemoteRepository.activeTv() != null
    }

    /**
     * Hook for the stream picker. Returns true if the launch was handed to the TV
     * (the caller must then skip local playback).
     */
    fun tryCast(launch: PlayerLaunch, notCastableMessage: String): Boolean {
        if (!isCastTargetActive()) return false
        if (!isShareableStreamUrl(launch.sourceUrl)) {
            TvRemoteLog.w("not castable (local/torrent url), playing on phone")
            NuvioToastController.show(notCastableMessage)
            return false
        }
        TvRemoteRepository.play(launch.toRemotePlayRequest())
        TvRemoteNavigation.open(TvRemoteDestination.Control)
        return true
    }
}

internal fun PlayerLaunch.toRemotePlayRequest(): RemotePlayRequest = RemotePlayRequest(
    media = RemoteMedia(
        type = contentType ?: parentMetaType,
        metaId = parentMetaId,
        metaType = parentMetaType,
        videoId = videoId,
        season = seasonNumber,
        episode = episodeNumber,
        title = title,
        episodeTitle = episodeTitle,
        poster = poster,
        background = background ?: episodeThumbnail,
        logo = logo,
    ),
    stream = RemoteStream(
        url = sourceUrl,
        headers = sourceHeaders,
        responseHeaders = sourceResponseHeaders,
        name = streamTitle,
        description = streamSubtitle,
        addonName = providerName,
        addonId = providerAddonId,
        filename = sourceFilename,
        videoSize = stream?.behaviorHints?.videoSize,
        bingeGroup = bingeGroup,
        streamType = streamType,
        subtitles = externalSubtitles.mapIndexed { index, subtitle ->
            RemoteSubtitle(
                id = "ext-$index",
                url = subtitle.url,
                lang = subtitle.language,
                label = subtitle.name,
                headers = subtitle.headers.orEmpty(),
            )
        },
    ),
    startPositionMs = initialPositionMs,
)
