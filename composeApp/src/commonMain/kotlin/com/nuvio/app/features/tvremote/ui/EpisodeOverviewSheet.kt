package com.nuvio.app.features.tvremote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.NuvioModalBottomSheet
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.details.MetaScreenSettingsRepository
import com.nuvio.app.features.details.MetaVideo
import com.nuvio.app.features.extras.rememberEpisodeBlurRadius
import com.nuvio.app.features.watched.WatchedRepository
import com.nuvio.app.features.watching.application.WatchingState
import com.nuvio.app.features.watchprogress.WatchProgressEntry
import com.nuvio.app.features.watchprogress.WatchProgressRepository
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.tv_remote_episodes
import nuvio.composeapp.generated.resources.tv_remote_episodes_loading
import nuvio.composeapp.generated.resources.tv_remote_episodes_season
import nuvio.composeapp.generated.resources.tv_remote_episodes_specials
import nuvio.composeapp.generated.resources.tv_remote_episodes_unavailable
import org.jetbrains.compose.resources.stringResource

/**
 * All episodes of a series by season, to pick another one to play. Unwatched episodes follow the
 * "blur unwatched episodes" setting like the detail page. [onSelect] gets the episode and the
 * position to resume it at (0 when it was not started or is finished).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EpisodeOverviewSheet(
    metaType: String,
    metaId: String,
    currentVideoId: String?,
    currentSeason: Int?,
    ambient: Color,
    onSelect: (meta: MetaDetails, episode: MetaVideo, resumePositionMs: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val load by produceState(
        MetaLoad(MetaDetailsRepository.peek(metaType, metaId)),
        metaType,
        metaId,
    ) {
        if (!value.finished) value = MetaLoad(runCatching { MetaDetailsRepository.fetch(metaType, metaId) }.getOrNull(), finished = true)
    }
    val meta = load.meta
    val loadFinished = load.finished
    val metaSettings by remember {
        MetaScreenSettingsRepository.ensureLoaded()
        MetaScreenSettingsRepository.uiState
    }.collectAsStateWithLifecycle()
    val watchedUiState by remember {
        WatchedRepository.ensureLoaded()
        WatchedRepository.uiState
    }.collectAsStateWithLifecycle()
    val progressUiState by remember {
        WatchProgressRepository.ensureLoaded()
        WatchProgressRepository.uiState
    }.collectAsStateWithLifecycle()
    val progressByVideoId = remember(progressUiState.entries, metaId) { progressUiState.byVideoIdForContent(metaId) }

    val episodes = meta?.videos.orEmpty().filter { it.season != null && it.episode != null }
    // Regular seasons first, specials (season 0) last.
    val seasons = episodes.mapNotNull { it.season }.distinct().sortedWith(compareBy({ it == 0 }, { it }))
    var selectedSeason by rememberSaveable(metaId) { mutableStateOf(currentSeason) }
    val season = selectedSeason?.takeIf { it in seasons } ?: seasons.firstOrNull()
    val seasonEpisodes = episodes.filter { it.season == season }.sortedBy { it.episode }

    NuvioModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = TvSheetColor,
        contentColor = Color.White,
    ) {
        TvSheetHeader(
            icon = Icons.AutoMirrored.Rounded.ViewList,
            title = stringResource(Res.string.tv_remote_episodes),
            subtitle = meta?.name,
            ambient = ambient,
        )
        when {
            meta == null && !loadFinished -> Row(
                modifier = Modifier.fillMaxWidth().padding(NuvioTokens.Space.s20),
                horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                Text(stringResource(Res.string.tv_remote_episodes_loading), color = Color.White.copy(alpha = 0.7f))
            }
            seasonEpisodes.isEmpty() -> Text(
                stringResource(Res.string.tv_remote_episodes_unavailable),
                modifier = Modifier.padding(NuvioTokens.Space.s20),
                color = Color.White.copy(alpha = 0.7f),
            )
            else -> {
                val seasonsState = rememberLazyListState(initialFirstVisibleItemIndex = seasons.indexOf(season).coerceAtLeast(0))
                LazyRow(
                    state = seasonsState,
                    contentPadding = PaddingValues(horizontal = NuvioTokens.Space.s16),
                    horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
                ) {
                    items(seasons, key = { it }) { number ->
                        SeasonChip(
                            label = if (number == 0) {
                                stringResource(Res.string.tv_remote_episodes_specials)
                            } else {
                                stringResource(Res.string.tv_remote_episodes_season, number)
                            },
                            selected = number == season,
                            ambient = ambient,
                            onClick = { selectedSeason = number },
                        )
                    }
                }
                Spacer(Modifier.height(NuvioTokens.Space.s8))
                val currentIndex = seasonEpisodes.indexOfFirst { it.id == currentVideoId }
                val episodesState = rememberLazyListState(initialFirstVisibleItemIndex = currentIndex.coerceAtLeast(0))
                LazyColumn(
                    state = episodesState,
                    modifier = Modifier.heightIn(max = 560.dp),
                    contentPadding = PaddingValues(
                        start = NuvioTokens.Space.s12,
                        end = NuvioTokens.Space.s12,
                        bottom = NuvioTokens.Space.s24,
                    ),
                    verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
                ) {
                    items(seasonEpisodes, key = { it.id }) { episode ->
                        val progress = progressByVideoId[episode.id]
                        val loadedMeta = meta ?: return@items
                        val watched = progress?.isEffectivelyCompleted == true ||
                            WatchingState.isEpisodeWatched(
                                watchedKeys = watchedUiState.watchedKeys,
                                metaType = loadedMeta.type,
                                metaId = loadedMeta.id,
                                episode = episode,
                            )
                        EpisodeRow(
                            episode = episode,
                            fallbackImage = loadedMeta.background ?: loadedMeta.poster,
                            progress = progress,
                            watched = watched,
                            current = episode.id == currentVideoId,
                            blurred = com.nuvio.app.features.extras.rememberBlurUnwatchedEpisodes() && !watched,
                            ambient = ambient,
                            onClick = {
                                val resume = progress?.takeIf { !it.isEffectivelyCompleted }?.lastPositionMs ?: 0L
                                onSelect(loadedMeta, episode, resume)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeasonChip(label: String, selected: Boolean, ambient: Color, onClick: () -> Unit) {
    val shape = CircleShape
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) ambient.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = if (selected) 0.3f else 0.08f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = NuvioTokens.Space.s14, vertical = NuvioTokens.Space.s8),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = Color.White,
        )
    }
}

@Composable
private fun EpisodeRow(
    episode: MetaVideo,
    fallbackImage: String?,
    progress: WatchProgressEntry?,
    watched: Boolean,
    current: Boolean,
    blurred: Boolean,
    ambient: Color,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (current) ambient.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.04f))
            .border(1.dp, Color.White.copy(alpha = if (current) 0.22f else 0.06f), shape)
            .clickable(onClick = onClick)
            .padding(NuvioTokens.Space.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val imageShape = RoundedCornerShape(10.dp)
        Box(
            modifier = Modifier
                .width(128.dp)
                .height(72.dp)
                .clip(imageShape)
                .background(Color.White.copy(alpha = 0.06f)),
        ) {
            val image = episode.thumbnail?.takeIf { it.isNotBlank() } ?: fallbackImage
            if (image != null) {
                AsyncImage(
                    model = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .then(if (blurred) Modifier.blur(rememberEpisodeBlurRadius()) else Modifier),
                )
            }
            val fraction = progress?.takeIf { !it.isEffectivelyCompleted && it.durationMs > 0L }
                ?.let { (it.lastPositionMs.toFloat() / it.durationMs).coerceIn(0f, 1f) }
            if (fraction != null && fraction > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.Black.copy(alpha = 0.5f)),
                ) {
                    Box(Modifier.fillMaxWidth(fraction).height(3.dp).background(Color.White))
                }
            }
        }
        Spacer(Modifier.width(NuvioTokens.Space.s12))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${episode.episode}. ${episode.title}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (current) FontWeight.Bold else FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val details = episode.overview?.takeIf { it.isNotBlank() } ?: episode.released?.take(10)
            if (details != null) {
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (watched) {
            Spacer(Modifier.width(NuvioTokens.Space.s8))
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private data class MetaLoad(val meta: MetaDetails?, val finished: Boolean = meta != null)
