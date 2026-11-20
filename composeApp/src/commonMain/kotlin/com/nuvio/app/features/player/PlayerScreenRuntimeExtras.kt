package com.nuvio.app.features.player

import com.nuvio.app.features.anime.AnimeContent
import com.nuvio.app.features.downloads.DownloadsRepository
import com.nuvio.app.features.extras.ExtrasPrompts
import com.nuvio.app.features.extras.ExtrasSettingsRepository
import com.nuvio.app.features.extras.RatingRequest
import com.nuvio.app.features.extras.isAnimeContent
import com.nuvio.app.features.extras.personalRatingKey
import kotlin.math.abs

/** Tracks which playback the anime speed was last decided for (one player at a time). */
private object PlayerExtrasState {
    var decidedKey: PlaybackKey? = null
    /** Whether the last decision had the title's metadata; if not, it is re-checked once it loads. */
    var decidedWithMeta = false
    var animeSpeedActive = false
}

/** Keeps the Rate button's target current and applies the anime speed once per item and source. */
internal fun PlayerScreenRuntime.applyPlayerExtras(snapshot: PlayerPlaybackSnapshot) {
    val isEpisode = activeSeasonNumber != null && activeEpisodeNumber != null && !activeVideoId.isNullOrBlank()
    val label = listOfNotNull(
        title.takeIf { it.isNotBlank() },
        if (isEpisode) "S${activeSeasonNumber}E${activeEpisodeNumber}" else null,
        activeEpisodeTitle?.takeIf { isEpisode && it.isNotBlank() },
    ).joinToString(" · ")
    ExtrasPrompts.currentPlayerTarget = personalRatingKey(parentMetaId, activeVideoId, isEpisode)
        ?.let { RatingRequest(it, label) }

    if (snapshot.isLoading || !snapshot.isPlaying) return
    val key = activePlaybackKey
    val meta = metaUiState.meta ?: playerMeta
    // Downloads often start before the metadata has loaded (or offline), so a decision made
    // without metadata is revisited once it arrives, but only to turn the anime speed on.
    val recheck = PlayerExtrasState.decidedKey == key
    if (recheck && (PlayerExtrasState.decidedWithMeta || meta == null)) return
    PlayerExtrasState.decidedKey = key
    PlayerExtrasState.decidedWithMeta = meta != null
    if (isPartySynced) return
    val controller = playerController ?: return
    val settings = ExtrasSettingsRepository.reload()
    val anime = settings.animeSpeedEnabled && (
        isAnimeContent(
            id = parentMetaId,
            type = contentType ?: parentMetaType,
            genres = meta?.genres.orEmpty(),
            country = meta?.country,
            language = meta?.language,
        ) ||
            DownloadsRepository.isAnimeDownload(parentMetaId) ||
            AnimeContent.isAnimeRelease(activeStreamTitle, activeSourceUrl.substringAfterLast('/'))
        )
    if (recheck && !anime) return
    if (anime) {
        PlayerExtrasState.animeSpeedActive = true
        if (abs(snapshot.playbackSpeed - settings.animeSpeed) > 0.01f) controller.setPlaybackSpeed(settings.animeSpeed)
    } else if (PlayerExtrasState.animeSpeedActive) {
        // Don't carry an anime speed over to the next, non-anime title.
        PlayerExtrasState.animeSpeedActive = false
        if (abs(snapshot.playbackSpeed - 1f) > 0.01f) controller.setPlaybackSpeed(1f)
    }
}
