package com.nuvio.app.features.tvremote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.ui.graphics.vector.ImageVector
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.tvremote.PairedTv
import com.nuvio.app.features.tvremote.TvConnectionStatus
import com.nuvio.app.features.tvremote.TvSessionState
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.tv_remote_status_connected
import nuvio.composeapp.generated.resources.tv_remote_status_connecting
import nuvio.composeapp.generated.resources.tv_remote_status_demo
import nuvio.composeapp.generated.resources.tv_remote_status_unauthorized
import nuvio.composeapp.generated.resources.tv_remote_status_unreachable
import org.jetbrains.compose.resources.stringResource

/** A computer running Nuvio gets a computer icon, TVs a TV. */
internal fun targetIcon(desktop: Boolean): ImageVector = if (desktop) Icons.Rounded.Computer else Icons.Rounded.Tv

/** Connection line for a TV: live status for the active one, address otherwise. */
@Composable
internal fun tvStatusText(tv: PairedTv, session: TvSessionState, isActive: Boolean): String {
    val address = if (tv.demo) stringResource(Res.string.tv_remote_status_demo) else tv.host
    if (!isActive || session.tvId != tv.id) return address
    val status = when (session.connection) {
        TvConnectionStatus.Connected -> stringResource(Res.string.tv_remote_status_connected)
        TvConnectionStatus.Connecting,
        TvConnectionStatus.Disconnected -> stringResource(Res.string.tv_remote_status_connecting)
        TvConnectionStatus.Unreachable -> stringResource(Res.string.tv_remote_status_unreachable)
        TvConnectionStatus.Unauthorized -> stringResource(Res.string.tv_remote_status_unauthorized)
    }
    return "$status · $address"
}

@Composable
internal fun connectionColor(status: TvConnectionStatus): Color {
    val colors = MaterialTheme.nuvio.colors
    return when (status) {
        TvConnectionStatus.Connected -> colors.success
        TvConnectionStatus.Connecting, TvConnectionStatus.Disconnected -> colors.warning
        TvConnectionStatus.Unreachable, TvConnectionStatus.Unauthorized -> colors.danger
    }
}

@Composable
internal fun ConnectionDot(status: TvConnectionStatus, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(8.dp)
            .background(connectionColor(status), CircleShape),
    )
}

internal fun formatPlaybackTime(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1000L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    val mm = minutes.toString().padStart(2, '0')
    val ss = seconds.toString().padStart(2, '0')
    return if (hours > 0) "$hours:$mm:$ss" else "$minutes:$ss"
}
