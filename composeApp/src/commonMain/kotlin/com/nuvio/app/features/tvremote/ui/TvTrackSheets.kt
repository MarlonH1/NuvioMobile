package com.nuvio.app.features.tvremote.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.ClosedCaptionDisabled
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.NuvioModalBottomSheet
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.features.tvremote.TvRemoteRepository
import com.nuvio.app.features.tvremote.protocol.RemotePlaybackState
import com.nuvio.app.features.tvremote.protocol.RemoteSubtitleSource
import com.nuvio.app.features.tvremote.protocol.RemoteTrack
import nuvio.composeapp.generated.resources.Res
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import nuvio.composeapp.generated.resources.tv_remote_subtitle_style
import nuvio.composeapp.generated.resources.tv_remote_audio
import nuvio.composeapp.generated.resources.tv_remote_no_tracks
import nuvio.composeapp.generated.resources.tv_remote_reset
import nuvio.composeapp.generated.resources.tv_remote_source_addon
import nuvio.composeapp.generated.resources.tv_remote_source_embedded
import nuvio.composeapp.generated.resources.tv_remote_source_external
import nuvio.composeapp.generated.resources.tv_remote_subtitle_delay
import nuvio.composeapp.generated.resources.tv_remote_subtitles
import nuvio.composeapp.generated.resources.tv_remote_subtitles_off
import org.jetbrains.compose.resources.stringResource

private const val SUBTITLE_DELAY_STEP_MS = 250L

/** Dark surface shared by all TV remote sheets, independent of the app theme. */
internal val TvSheetColor = Color(0xFF121318)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TvSubtitleSheet(playback: RemotePlaybackState, ambient: Color, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    NuvioModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = TvSheetColor,
        contentColor = Color.White,
    ) {
        // Scrolls as a whole once the style controls are expanded.
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            TvSubtitleSheetContent(playback = playback, ambient = ambient)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TvAudioSheet(playback: RemotePlaybackState, ambient: Color, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    NuvioModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = TvSheetColor,
        contentColor = Color.White,
    ) {
        TvAudioSheetContent(playback = playback, ambient = ambient)
    }
}

@Composable
internal fun ColumnScope.TvSubtitleSheetContent(playback: RemotePlaybackState, ambient: Color) {
    val anySelected = playback.subtitleTracks.any { it.selected }
    TvSheetHeader(
        icon = Icons.Rounded.ClosedCaption,
        title = stringResource(Res.string.tv_remote_subtitles),
        subtitle = playback.subtitleTracks.firstOrNull { it.selected }?.label
            ?: stringResource(Res.string.tv_remote_subtitles_off),
        ambient = ambient,
    )
    Column(
        modifier = Modifier
            .heightIn(max = 360.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = NuvioTokens.Space.s12),
        verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
    ) {
        TvOptionRow(
            title = stringResource(Res.string.tv_remote_subtitles_off),
            icon = Icons.Rounded.ClosedCaptionDisabled,
            selected = !anySelected,
            ambient = ambient,
            onClick = { TvRemoteRepository.selectSubtitleTrack(null) },
        )
        playback.subtitleTracks.forEach { track ->
            TrackRow(track = track, ambient = ambient) { TvRemoteRepository.selectSubtitleTrack(track.id) }
        }
    }
    SubtitleDelayCard(
        delayMs = playback.subtitleDelayMs,
        enabled = anySelected,
        modifier = Modifier.padding(NuvioTokens.Space.s12),
    )
    var styleOpen by remember { mutableStateOf(false) }
    Box(modifier = Modifier.padding(horizontal = NuvioTokens.Space.s12)) {
        TvOptionRow(
            title = stringResource(Res.string.tv_remote_subtitle_style),
            icon = Icons.Rounded.FormatSize,
            selected = false,
            ambient = ambient,
            trailing = {
                Icon(
                    imageVector = if (styleOpen) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                )
            },
            onClick = { styleOpen = !styleOpen },
        )
    }
    if (styleOpen) TvSubtitleStyleControls(playback)
}

@Composable
internal fun ColumnScope.TvAudioSheetContent(playback: RemotePlaybackState, ambient: Color) {
    TvSheetHeader(
        icon = Icons.Rounded.Audiotrack,
        title = stringResource(Res.string.tv_remote_audio),
        subtitle = playback.audioTracks.firstOrNull { it.selected }?.label,
        ambient = ambient,
    )
    Column(
        modifier = Modifier
            .heightIn(max = 420.dp)
            .verticalScroll(rememberScrollState())
            .padding(start = NuvioTokens.Space.s12, end = NuvioTokens.Space.s12, bottom = NuvioTokens.Space.s16),
        verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
    ) {
        if (playback.audioTracks.isEmpty()) {
            Text(
                text = stringResource(Res.string.tv_remote_no_tracks),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.55f),
                modifier = Modifier.padding(NuvioTokens.Space.s8),
            )
        }
        playback.audioTracks.forEach { track ->
            TrackRow(track = track, ambient = ambient) { TvRemoteRepository.selectAudioTrack(track.id) }
        }
    }
}

/** Sheet title with a tinted icon tile and the current value underneath. */
@Composable
internal fun TvSheetHeader(icon: ImageVector, title: String, subtitle: String?, ambient: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = NuvioTokens.Space.s20, vertical = NuvioTokens.Space.s14),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(ambient, ambient.copy(alpha = 0.45f)))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(NuvioTokens.Space.s14))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Selectable row: glass card, tinted with the media color when selected. */
@Composable
internal fun TvOptionRow(
    title: String,
    selected: Boolean,
    ambient: Color,
    icon: ImageVector? = null,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val background by animateColorAsState(
        if (selected) ambient.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.04f),
        label = "tv_option_background",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .border(1.dp, Color.White.copy(alpha = if (selected) 0.22f else 0.06f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = NuvioTokens.Space.s14, vertical = NuvioTokens.Space.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = if (selected) 1f else 0.6f),
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(NuvioTokens.Space.s12))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun TrackRow(track: RemoteTrack, ambient: Color, onClick: () -> Unit) {
    val sourceLabel = when (track.source) {
        RemoteSubtitleSource.Embedded -> stringResource(Res.string.tv_remote_source_embedded)
        RemoteSubtitleSource.Addon -> stringResource(Res.string.tv_remote_source_addon)
        RemoteSubtitleSource.External -> stringResource(Res.string.tv_remote_source_external)
        null -> null
    }
    val language = track.lang?.uppercase()?.takeIf { it.isNotBlank() }
    TvOptionRow(
        title = track.label,
        subtitle = sourceLabel,
        selected = track.selected,
        ambient = ambient,
        trailing = { language?.let { TvChip(it) } },
        onClick = onClick,
    )
}

@Composable
internal fun TvChip(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = Color.White.copy(alpha = 0.8f),
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun SubtitleDelayCard(delayMs: Long, enabled: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
            .alpha(if (enabled) 1f else 0.4f)
            .padding(NuvioTokens.Space.s14),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(Res.string.tv_remote_subtitle_delay),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            if (delayMs != 0L) {
                Text(
                    text = stringResource(Res.string.tv_remote_reset),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(enabled = enabled) { TvRemoteRepository.setSubtitleDelay(0L) }
                        .padding(horizontal = NuvioTokens.Space.s8, vertical = NuvioTokens.Space.s4),
                )
            }
        }
        Spacer(Modifier.height(NuvioTokens.Space.s10))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepButton(Icons.Rounded.Remove, enabled) {
                TvRemoteRepository.setSubtitleDelay(delayMs - SUBTITLE_DELAY_STEP_MS)
            }
            Text(
                text = formatDelay(delayMs),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            StepButton(Icons.Rounded.Add, enabled) {
                TvRemoteRepository.setSubtitleDelay(delayMs + SUBTITLE_DELAY_STEP_MS)
            }
        }
    }
}

@Composable
private fun StepButton(icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .tvGlass(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = Color.White)
    }
}

private fun formatDelay(delayMs: Long): String {
    val sign = if (delayMs > 0) "+" else if (delayMs < 0) "−" else ""
    val abs = kotlin.math.abs(delayMs)
    val seconds = abs / 1000
    val hundredths = (abs % 1000) / 10
    return "$sign$seconds.${hundredths.toString().padStart(2, '0')} s"
}
