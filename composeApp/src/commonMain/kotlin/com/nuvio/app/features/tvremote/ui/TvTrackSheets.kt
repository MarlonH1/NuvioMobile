package com.nuvio.app.features.tvremote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.NuvioBottomSheetActionRow
import com.nuvio.app.core.ui.NuvioBottomSheetDivider
import com.nuvio.app.core.ui.NuvioInfoBadge
import com.nuvio.app.core.ui.NuvioModalBottomSheet
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.tvremote.TvRemoteRepository
import com.nuvio.app.features.tvremote.protocol.RemotePlaybackState
import com.nuvio.app.features.tvremote.protocol.RemoteSubtitleSource
import com.nuvio.app.features.tvremote.protocol.RemoteTrack
import nuvio.composeapp.generated.resources.Res
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TvSubtitleSheet(playback: RemotePlaybackState, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val anySelected = playback.subtitleTracks.any { it.selected }
    NuvioModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        SheetTitle(stringResource(Res.string.tv_remote_subtitles))
        Column(
            modifier = Modifier
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            NuvioBottomSheetActionRow(
                title = stringResource(Res.string.tv_remote_subtitles_off),
                selected = !anySelected,
                trailingContent = { if (!anySelected) SelectedCheck() },
                onClick = { TvRemoteRepository.selectSubtitleTrack(null) },
            )
            playback.subtitleTracks.forEach { track ->
                TrackRow(track = track) { TvRemoteRepository.selectSubtitleTrack(track.id) }
            }
        }
        Spacer(Modifier.padding(top = NuvioTokens.Space.s8))
        NuvioBottomSheetDivider()
        SubtitleDelayRow(delayMs = playback.subtitleDelayMs)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TvAudioSheet(playback: RemotePlaybackState, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    NuvioModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        SheetTitle(stringResource(Res.string.tv_remote_audio))
        Column(
            modifier = Modifier
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = NuvioTokens.Space.s16),
        ) {
            if (playback.audioTracks.isEmpty()) {
                EmptyTracks()
            }
            playback.audioTracks.forEach { track ->
                TrackRow(track = track) { TvRemoteRepository.selectAudioTrack(track.id) }
            }
        }
    }
}

@Composable
private fun SheetTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.nuvio.colors.textPrimary,
        modifier = Modifier.padding(horizontal = NuvioTokens.Space.s20, vertical = NuvioTokens.Space.s12),
    )
}

@Composable
private fun EmptyTracks() {
    Text(
        text = stringResource(Res.string.tv_remote_no_tracks),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.nuvio.colors.textMuted,
        modifier = Modifier.padding(horizontal = NuvioTokens.Space.s20, vertical = NuvioTokens.Space.s12),
    )
}

@Composable
private fun TrackRow(track: RemoteTrack, onClick: () -> Unit) {
    val sourceLabel = when (track.source) {
        RemoteSubtitleSource.Embedded -> stringResource(Res.string.tv_remote_source_embedded)
        RemoteSubtitleSource.Addon -> stringResource(Res.string.tv_remote_source_addon)
        RemoteSubtitleSource.External -> stringResource(Res.string.tv_remote_source_external)
        null -> null
    }
    val language = track.lang?.uppercase()?.takeIf { it.isNotBlank() && !track.label.contains(it, ignoreCase = true) }
    NuvioBottomSheetActionRow(
        title = track.label,
        selected = track.selected,
        trailingContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                language?.let { NuvioInfoBadge(text = it) }
                sourceLabel?.let { NuvioInfoBadge(text = it) }
                if (track.selected) SelectedCheck()
            }
        },
        onClick = onClick,
    )
}

@Composable
private fun SubtitleDelayRow(delayMs: Long) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = NuvioTokens.Space.s20, vertical = NuvioTokens.Space.s16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.tv_remote_subtitle_delay),
                style = MaterialTheme.typography.bodyLarge,
                color = tokens.colors.textPrimary,
            )
            if (delayMs != 0L) {
                Text(
                    text = stringResource(Res.string.tv_remote_reset),
                    style = MaterialTheme.typography.labelMedium,
                    color = tokens.colors.accent,
                    modifier = Modifier
                        .clip(tokens.shapes.chip)
                        .clickable { TvRemoteRepository.setSubtitleDelay(0L) }
                        .padding(vertical = NuvioTokens.Space.s4),
                )
            }
        }
        StepButton(Icons.Rounded.Remove) { TvRemoteRepository.setSubtitleDelay(delayMs - SUBTITLE_DELAY_STEP_MS) }
        Text(
            text = formatDelay(delayMs),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = tokens.colors.textPrimary,
            modifier = Modifier.padding(horizontal = NuvioTokens.Space.s12),
        )
        StepButton(Icons.Rounded.Add) { TvRemoteRepository.setSubtitleDelay(delayMs + SUBTITLE_DELAY_STEP_MS) }
    }
}

@Composable
private fun StepButton(icon: ImageVector, onClick: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(tokens.colors.surfaceCard)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tokens.colors.textPrimary)
    }
}

@Composable
private fun SelectedCheck() {
    Icon(
        imageVector = Icons.Rounded.Check,
        contentDescription = null,
        tint = MaterialTheme.nuvio.colors.accent,
        modifier = Modifier.size(NuvioTokens.Icon.md),
    )
}

private fun formatDelay(delayMs: Long): String {
    val sign = if (delayMs > 0) "+" else if (delayMs < 0) "−" else ""
    val abs = kotlin.math.abs(delayMs)
    val seconds = abs / 1000
    val tenths = (abs % 1000) / 10
    return "$sign$seconds.${tenths.toString().padStart(2, '0')} s"
}
