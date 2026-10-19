package com.nuvio.app.features.tvremote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioBottomSheetActionRow
import com.nuvio.app.core.ui.NuvioBottomSheetDivider
import com.nuvio.app.core.ui.NuvioModalBottomSheet
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.dismissNuvioBottomSheet
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.tvremote.TvRemoteDestination
import com.nuvio.app.features.tvremote.TvRemoteNavigation
import com.nuvio.app.features.tvremote.TvRemoteRepository
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.tv_remote_cast_button
import nuvio.composeapp.generated.resources.tv_remote_open_remote
import nuvio.composeapp.generated.resources.tv_remote_target_manage
import nuvio.composeapp.generated.resources.tv_remote_target_sheet_title
import nuvio.composeapp.generated.resources.tv_remote_target_this_device
import org.jetbrains.compose.resources.stringResource

/**
 * Round cast button for overlay headers. Renders nothing while the TV remote
 * feature is switched off, so it can be dropped into upstream screens safely.
 */
@Composable
fun TvCastButton(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.background.copy(alpha = 0.45f),
    contentColor: Color = MaterialTheme.colorScheme.onBackground,
) {
    LaunchedEffect(Unit) { TvRemoteRepository.ensureLoaded() }
    val enabled by TvRemoteRepository.enabled.collectAsStateWithLifecycle()
    if (!enabled) return
    val activeTvId by TvRemoteRepository.activeTvId.collectAsStateWithLifecycle()
    var showSheet by remember { mutableStateOf(false) }
    val tokens = MaterialTheme.nuvio

    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (activeTvId != null) tokens.colors.accent else containerColor)
            .clickable { showSheet = true },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (activeTvId != null) Icons.Rounded.CastConnected else Icons.Rounded.Cast,
            contentDescription = stringResource(Res.string.tv_remote_cast_button),
            tint = if (activeTvId != null) tokens.colors.onAccent else contentColor,
            modifier = Modifier.size(NuvioTokens.Icon.md),
        )
    }

    if (showSheet) {
        TvTargetSheet(onDismiss = { showSheet = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TvTargetSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val tvs by TvRemoteRepository.pairedTvs.collectAsStateWithLifecycle()
    val activeTvId by TvRemoteRepository.activeTvId.collectAsStateWithLifecycle()
    val session by TvRemoteRepository.session.collectAsStateWithLifecycle()
    val tokens = MaterialTheme.nuvio

    fun dismissThen(action: () -> Unit) {
        scope.launch {
            dismissNuvioBottomSheet(sheetState) {
                onDismiss()
                action()
            }
        }
    }

    NuvioModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(bottom = NuvioTokens.Space.s16)) {
            Text(
                text = stringResource(Res.string.tv_remote_target_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = tokens.colors.textPrimary,
                modifier = Modifier.padding(horizontal = NuvioTokens.Space.s20, vertical = NuvioTokens.Space.s12),
            )
            NuvioBottomSheetActionRow(
                title = stringResource(Res.string.tv_remote_target_this_device),
                icon = Icons.Rounded.Smartphone,
                selected = activeTvId == null,
                trailingContent = { if (activeTvId == null) SelectedCheck() },
                onClick = { dismissThen { TvRemoteRepository.selectTarget(null) } },
            )
            tvs.forEach { tv ->
                val isActive = tv.id == activeTvId
                NuvioBottomSheetActionRow(
                    title = tv.name,
                    icon = Icons.Rounded.Tv,
                    selected = isActive,
                    trailingContent = { if (isActive) SelectedCheck() },
                    onClick = { dismissThen { TvRemoteRepository.selectTarget(tv.id) } },
                )
                Text(
                    text = tvStatusText(tv, session, isActive),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 74.dp, end = NuvioTokens.Space.s20)
                        .padding(bottom = NuvioTokens.Space.s4),
                )
            }
            Spacer(Modifier.height(NuvioTokens.Space.s8))
            NuvioBottomSheetDivider()
            Spacer(Modifier.height(NuvioTokens.Space.s8))
            if (session.isPlaybackActive) {
                NuvioBottomSheetActionRow(
                    title = stringResource(Res.string.tv_remote_open_remote),
                    icon = Icons.Rounded.SettingsRemote,
                    onClick = { dismissThen { TvRemoteNavigation.open(TvRemoteDestination.Control) } },
                )
            }
            NuvioBottomSheetActionRow(
                title = stringResource(Res.string.tv_remote_target_manage),
                icon = Icons.Rounded.Settings,
                onClick = { dismissThen { TvRemoteNavigation.open(TvRemoteDestination.Devices) } },
            )
        }
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
