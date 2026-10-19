package com.nuvio.app.features.tvremote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.ChevronRight
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioModalBottomSheet
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.dismissNuvioBottomSheet
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.tvremote.PairedTv
import com.nuvio.app.features.tvremote.TvConnectionStatus
import com.nuvio.app.features.tvremote.TvRemoteDestination
import com.nuvio.app.features.tvremote.TvRemoteNavigation
import com.nuvio.app.features.tvremote.TvRemoteRepository
import com.nuvio.app.features.tvremote.TvSessionState
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.tv_remote_cast_button
import nuvio.composeapp.generated.resources.tv_remote_open_remote
import nuvio.composeapp.generated.resources.tv_remote_playing_on
import nuvio.composeapp.generated.resources.tv_remote_target_manage
import nuvio.composeapp.generated.resources.tv_remote_target_sheet_title
import nuvio.composeapp.generated.resources.tv_remote_target_this_device
import nuvio.composeapp.generated.resources.tv_remote_target_this_device_hint
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
    val active = activeTvId != null

    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (active) Color.White else containerColor)
            .clickable { showSheet = true },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (active) Icons.Rounded.CastConnected else Icons.Rounded.Cast,
            contentDescription = stringResource(Res.string.tv_remote_cast_button),
            tint = if (active) Color(0xFF0B0C0F) else contentColor,
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

    fun dismissThen(action: () -> Unit) {
        scope.launch {
            dismissNuvioBottomSheet(sheetState) {
                onDismiss()
                action()
            }
        }
    }

    NuvioModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = TvSheetColor,
        contentColor = Color.White,
    ) {
        TvTargetSheetContent(
            tvs = tvs,
            activeTvId = activeTvId,
            session = session,
            onSelect = { id -> dismissThen { TvRemoteRepository.selectTarget(id) } },
            onOpenRemote = { dismissThen { TvRemoteNavigation.open(TvRemoteDestination.Control) } },
            onManage = { dismissThen { TvRemoteNavigation.open(TvRemoteDestination.Devices) } },
        )
    }
}

@Composable
internal fun ColumnScope.TvTargetSheetContent(
    tvs: List<PairedTv>,
    activeTvId: String?,
    session: TvSessionState,
    onSelect: (String?) -> Unit,
    onOpenRemote: () -> Unit,
    onManage: () -> Unit,
) {
    val accent = MaterialTheme.nuvio.colors.accent
    val playingMedia = session.playback.media?.takeIf { session.isPlaybackActive }
    val activeTv = tvs.firstOrNull { it.id == activeTvId }
    TvSheetHeader(
        icon = if (activeTv != null) Icons.Rounded.CastConnected else Icons.Rounded.Cast,
        title = stringResource(Res.string.tv_remote_target_sheet_title),
        subtitle = activeTv?.name ?: stringResource(Res.string.tv_remote_target_this_device),
        ambient = fallbackAmbientColor(activeTv?.name ?: "phone"),
    )
    Column(
        modifier = Modifier.padding(horizontal = NuvioTokens.Space.s12),
        verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
    ) {
        TvOptionRow(
            title = stringResource(Res.string.tv_remote_target_this_device),
            subtitle = stringResource(Res.string.tv_remote_target_this_device_hint),
            icon = Icons.Rounded.Smartphone,
            selected = activeTvId == null,
            ambient = accent,
            onClick = { onSelect(null) },
        )
        tvs.forEach { tv ->
            val isActive = tv.id == activeTvId
            TvOptionRow(
                title = tv.name,
                subtitle = tvStatusText(tv, session, isActive),
                icon = Icons.Rounded.Tv,
                selected = isActive,
                ambient = fallbackAmbientColor(tv.name),
                trailing = if (isActive && session.tvId == tv.id) {
                    {
                        PulsingDot(
                            color = connectionColor(session.connection),
                            pulsing = session.connection == TvConnectionStatus.Connected,
                            size = 7.dp,
                        )
                    }
                } else {
                    null
                },
                onClick = { onSelect(tv.id) },
            )
        }
    }
    Spacer(Modifier.height(NuvioTokens.Space.s14))
    if (playingMedia != null && activeTv != null) {
        SheetLinkRow(
            icon = Icons.Rounded.SettingsRemote,
            title = stringResource(Res.string.tv_remote_open_remote),
            subtitle = stringResource(Res.string.tv_remote_playing_on, activeTv.name) + " · " + playingMedia.title,
            highlighted = true,
            onClick = onOpenRemote,
        )
    }
    SheetLinkRow(
        icon = Icons.Rounded.Settings,
        title = stringResource(Res.string.tv_remote_target_manage),
        onClick = onManage,
    )
    Spacer(Modifier.height(NuvioTokens.Space.s12))
}

@Composable
private fun SheetLinkRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    highlighted: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = NuvioTokens.Space.s12, vertical = 2.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .background(if (highlighted) Color.White else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = NuvioTokens.Space.s14, vertical = NuvioTokens.Space.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val content = if (highlighted) Color(0xFF0B0C0F) else Color.White
        Icon(imageVector = icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(NuvioTokens.Space.s12))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = content,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = content.copy(alpha = 0.6f),
                    maxLines = 1,
                )
            }
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = content.copy(alpha = 0.5f),
        )
    }
}
