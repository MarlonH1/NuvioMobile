package com.nuvio.app.features.tvremote.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.nuvioBottomNavigationBarInsets
import com.nuvio.app.features.tvremote.TvRemoteDestination
import com.nuvio.app.features.tvremote.TvRemoteNavigation
import com.nuvio.app.features.tvremote.TvRemoteRepository
import com.nuvio.app.features.tvremote.protocol.RemotePlaybackStatus
import kotlinx.coroutines.delay
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.tv_remote_pause
import nuvio.composeapp.generated.resources.tv_remote_play
import nuvio.composeapp.generated.resources.tv_remote_playing_on
import org.jetbrains.compose.resources.stringResource

/**
 * Floating "playing on TV" bar. Tapping opens the remote. Hidden while [visible] is
 * false (e.g. on the remote or player screen) or when nothing plays on the TV.
 */
@Composable
fun TvNowPlayingBar(visible: Boolean, modifier: Modifier = Modifier) {
    val session by TvRemoteRepository.session.collectAsStateWithLifecycle()
    val enabled by TvRemoteRepository.enabled.collectAsStateWithLifecycle()
    val tvs by TvRemoteRepository.pairedTvs.collectAsStateWithLifecycle()
    val show = visible && enabled && session.isPlaybackActive
    val tokens = MaterialTheme.nuvio
    val playback = session.playback
    val media = playback.media

    var nowMs by remember { mutableLongStateOf(TvRemoteRepository.now()) }
    LaunchedEffect(show, playback.status) {
        while (show && playback.status == RemotePlaybackStatus.Playing) {
            nowMs = TvRemoteRepository.now()
            delay(1_000)
        }
        nowMs = TvRemoteRepository.now()
    }

    val navBarBottom = nuvioBottomNavigationBarInsets().asPaddingValues().calculateBottomPadding()

    AnimatedVisibility(
        visible = show && media != null,
        modifier = modifier.padding(bottom = navBarBottom + NuvioTokens.Space.s72),
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
    ) {
        val tvName = tvs.firstOrNull { it.id == session.tvId }?.name.orEmpty()
        val isPlaying = playback.status == RemotePlaybackStatus.Playing
        val progress = if (playback.durationMs > 0L) {
            (session.positionAt(nowMs).toFloat() / playback.durationMs).coerceIn(0f, 1f)
        } else {
            0f
        }
        val shape = RoundedCornerShape(NuvioTokens.Space.s16)
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .padding(horizontal = NuvioTokens.Space.s12)
                .shadow(12.dp, shape)
                .clip(shape)
                .background(tokens.colors.surfaceElevated)
                .clickable { TvRemoteNavigation.open(TvRemoteDestination.Control) },
        ) {
            Row(
                modifier = Modifier.padding(NuvioTokens.Space.s8),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 52.dp)
                        .clip(RoundedCornerShape(NuvioTokens.Space.s6))
                        .background(tokens.colors.surfaceCard),
                ) {
                    media?.poster?.let { url ->
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(width = 36.dp, height = 52.dp),
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = media?.title.orEmpty(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = tokens.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s4),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CastConnected,
                            contentDescription = null,
                            tint = tokens.colors.accent,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = stringResource(Res.string.tv_remote_playing_on, tvName),
                            style = MaterialTheme.typography.labelMedium,
                            color = tokens.colors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(onClick = TvRemoteRepository::togglePause),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(
                            if (isPlaying) Res.string.tv_remote_pause else Res.string.tv_remote_play,
                        ),
                        tint = tokens.colors.textPrimary,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = tokens.colors.accent,
                trackColor = tokens.colors.playerTimelineTrack,
                drawStopIndicator = {},
            )
        }
    }
}
