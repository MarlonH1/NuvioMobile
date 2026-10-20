package com.nuvio.app.features.tvremote

import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.features.party.isShareableStreamUrl
import com.nuvio.app.features.player.PlayerLaunch
import com.nuvio.app.features.tvremote.protocol.RemoteMedia
import com.nuvio.app.features.tvremote.protocol.RemotePlayRequest
import com.nuvio.app.features.tvremote.protocol.RemoteStream
import com.nuvio.app.features.tvremote.protocol.RemoteSubtitle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TvRemoteDestination { Devices, Control }

/**
 * Lets feature code open TV remote screens without threading callbacks through the app.
 * A request waits until the navigation host consumes it, so one made before the UI exists
 * (notification tap on a cold start) is not lost.
 */
object TvRemoteNavigation {
    private val _pending = MutableStateFlow<TvRemoteDestination?>(null)
    val pending: StateFlow<TvRemoteDestination?> = _pending.asStateFlow()

    fun open(destination: TvRemoteDestination) {
        _pending.value = destination
    }

    fun consume() {
        _pending.value = null
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

internal fun PlayerLaunch.toRemotePlayRequest(): RemotePlayRequest {
    val meta = com.nuvio.app.features.details.MetaDetailsRepository.peek(parentMetaType, parentMetaId)
        ?: com.nuvio.app.features.details.MetaDetailsRepository.uiState.value.meta?.takeIf { it.id == parentMetaId }
    return toRemotePlayRequest(meta)
}

private fun PlayerLaunch.toRemotePlayRequest(meta: com.nuvio.app.features.details.MetaDetails?): RemotePlayRequest = RemotePlayRequest(
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
        genres = meta?.genres?.takeIf { it.isNotEmpty() },
        country = meta?.country,
        language = meta?.language,
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
