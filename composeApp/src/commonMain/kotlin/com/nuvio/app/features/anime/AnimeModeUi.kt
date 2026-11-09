package com.nuvio.app.features.anime

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Marker of the episode a screen is about (e.g. the source picker), for nested headers. */
val LocalEpisodeFillerStatus = androidx.compose.runtime.compositionLocalOf<FillerStatus?> { null }

/** Current marker for an episode (null when none or markers are off); loads saved show data. */
@Composable
fun rememberEpisodeFillerStatus(metaId: String?, season: Int?, episode: Int?, videoId: String? = null): FillerStatus? {
    val mode by remember { AnimeModeRepository.ensureLoaded(); AnimeModeRepository.state }.collectAsStateWithLifecycle()
    val byVideo by AnimeFillerRepository.statusByVideoId.collectAsStateWithLifecycle()
    val byEpisode by AnimeFillerRepository.statusByEpisode.collectAsStateWithLifecycle()
    LaunchedEffect(metaId) { AnimeFillerRepository.ensureShowLoaded(metaId) }
    if (!mode.showFillerBadges) return null
    return videoId?.let { byVideo[it] }
        ?: if (metaId != null && season != null && episode != null) {
            byEpisode[AnimeFillerRepository.episodeKey(metaId, season, episode)]
        } else {
            null
        }
}

/** Short label for the player's web controls ("FILLER" / "MIXED"), or "". */
@Composable
fun rememberEpisodeFillerLabel(metaId: String?, season: Int?, episode: Int?, videoId: String? = null): String =
    when (rememberEpisodeFillerStatus(metaId, season, episode, videoId)) {
        FillerStatus.FILLER -> "FILLER"
        FillerStatus.MIXED -> "MIXED"
        null -> ""
    }

/** FILLER / MIXED marker for an episode known by show + season + episode. */
@Composable
fun AnimeEpisodeStatusBadge(
    metaId: String?,
    season: Int?,
    episode: Int?,
    videoId: String? = null,
    modifier: Modifier = Modifier,
) {
    AnimeFillerStatusBadge(rememberEpisodeFillerStatus(metaId, season, episode, videoId), modifier)
}

/** Draws the marker for [status] (nothing for null). */
@Composable
fun AnimeFillerStatusBadge(status: FillerStatus?, modifier: Modifier = Modifier) {
    when (status) {
        FillerStatus.FILLER -> AnimeEpisodeBadge(
            text = "FILLER",
            dotColor = Color(0xFFF59E0B),
            borderColor = Color(0xFFF59E0B).copy(alpha = 0.55f),
            textColor = Color(0xFFFDE68A),
            modifier = modifier,
        )
        FillerStatus.MIXED -> AnimeEpisodeBadge(
            text = "MIXED",
            dotColor = Color(0xFF0EA5E9),
            borderColor = Color(0xFF0EA5E9).copy(alpha = 0.55f),
            textColor = Color(0xFFBAE6FD),
            modifier = modifier,
        )
        null -> Unit
    }
}

/** FILLER / MIXED marker for an episode, shown when filler markers are on. */
@Composable
fun AnimeEpisodeStatusBadge(videoId: String, modifier: Modifier = Modifier) {
    val mode by remember { AnimeModeRepository.ensureLoaded(); AnimeModeRepository.state }.collectAsStateWithLifecycle()
    val statuses by AnimeFillerRepository.statusByVideoId.collectAsStateWithLifecycle()
    AnimeFillerStatusBadge(if (mode.showFillerBadges) statuses[videoId] else null, modifier)
}

@Composable
private fun AnimeEpisodeBadge(
    text: String,
    dotColor: Color,
    borderColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(6.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.78f))
            .border(1.dp, borderColor, shape)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(dotColor))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.5.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.7.sp,
            ),
            color = textColor,
            maxLines = 1,
        )
    }
}

/** Effect that looks up filler data for an opened title. */
@Composable
fun AnimeFillerLookupEffect(meta: com.nuvio.app.features.details.MetaDetails?) {
    val mode by remember { AnimeModeRepository.ensureLoaded(); AnimeModeRepository.state }.collectAsStateWithLifecycle()
    val key = meta?.id to meta?.videos?.size
    LaunchedEffect(key, mode.showFillerBadges) {
        if (meta != null && mode.showFillerBadges) AnimeFillerRepository.resolve(meta)
    }
}

/** Returns [meta] without pure filler episodes when "Hide Filler Episodes" is on. */
@Composable
fun rememberAnimeFilteredMeta(meta: com.nuvio.app.features.details.MetaDetails): com.nuvio.app.features.details.MetaDetails {
    val mode by remember { AnimeModeRepository.ensureLoaded(); AnimeModeRepository.state }.collectAsStateWithLifecycle()
    val statuses by AnimeFillerRepository.statusByVideoId.collectAsStateWithLifecycle()
    return remember(meta, mode.hidingFillers, statuses) {
        if (!mode.hidingFillers) return@remember meta
        val filtered = meta.videos.filterNot { statuses[it.id] == FillerStatus.FILLER }
        if (filtered.size == meta.videos.size) meta else meta.copy(videos = filtered)
    }
}
