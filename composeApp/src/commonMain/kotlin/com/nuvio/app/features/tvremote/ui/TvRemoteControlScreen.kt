package com.nuvio.app.features.tvremote.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.NuvioBackButton
import com.nuvio.app.core.ui.NuvioPrimaryButton
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.tvremote.TvConnectionStatus
import com.nuvio.app.features.tvremote.TvRemoteDestination
import com.nuvio.app.features.tvremote.TvRemoteNavigation
import com.nuvio.app.features.tvremote.TvRemoteRepository
import com.nuvio.app.features.tvremote.TvSessionState
import com.nuvio.app.features.tvremote.protocol.RemoteMedia
import com.nuvio.app.features.tvremote.protocol.RemotePlaybackStatus
import kotlinx.coroutines.delay
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.tv_remote_audio
import nuvio.composeapp.generated.resources.tv_remote_buffering
import nuvio.composeapp.generated.resources.tv_remote_connected_to
import nuvio.composeapp.generated.resources.tv_remote_ended
import nuvio.composeapp.generated.resources.tv_remote_episode_label
import nuvio.composeapp.generated.resources.tv_remote_error_not_connected
import nuvio.composeapp.generated.resources.tv_remote_error_playback
import nuvio.composeapp.generated.resources.tv_remote_forward
import nuvio.composeapp.generated.resources.tv_remote_loading
import nuvio.composeapp.generated.resources.tv_remote_mute
import nuvio.composeapp.generated.resources.tv_remote_next_episode
import nuvio.composeapp.generated.resources.tv_remote_nothing_playing
import nuvio.composeapp.generated.resources.tv_remote_nothing_playing_hint
import nuvio.composeapp.generated.resources.tv_remote_pause
import nuvio.composeapp.generated.resources.tv_remote_play
import nuvio.composeapp.generated.resources.tv_remote_playing_on
import nuvio.composeapp.generated.resources.tv_remote_repair
import nuvio.composeapp.generated.resources.tv_remote_retry
import nuvio.composeapp.generated.resources.tv_remote_rewind
import nuvio.composeapp.generated.resources.tv_remote_status_unauthorized
import nuvio.composeapp.generated.resources.tv_remote_stop
import nuvio.composeapp.generated.resources.tv_remote_subtitles
import nuvio.composeapp.generated.resources.tv_remote_unmute
import nuvio.composeapp.generated.resources.tv_remote_volume
import nuvio.composeapp.generated.resources.tv_remote_volume_down
import nuvio.composeapp.generated.resources.tv_remote_volume_up
import org.jetbrains.compose.resources.stringResource

/** Matches the hard-coded ±10 s of the phone player (PlayerScreenRuntimeUi). */
private const val SKIP_SECONDS = 10

private enum class TrackSheet { Subtitles, Audio }

@Composable
fun TvRemoteControlScreen(onBack: () -> Unit) {
    LaunchedEffect(Unit) { TvRemoteRepository.ensureLoaded() }
    val session by TvRemoteRepository.session.collectAsStateWithLifecycle()
    val tvs by TvRemoteRepository.pairedTvs.collectAsStateWithLifecycle()
    val tvName = tvs.firstOrNull { it.id == session.tvId }?.name.orEmpty()
    val playback = session.playback
    val media = playback.media
    val tokens = MaterialTheme.nuvio
    var openSheet by remember { mutableStateOf<TrackSheet?>(null) }

    // Ticks the interpolated position while the TV plays.
    var nowMs by remember { mutableLongStateOf(TvRemoteRepository.now()) }
    LaunchedEffect(playback.status) {
        while (playback.status == RemotePlaybackStatus.Playing) {
            nowMs = TvRemoteRepository.now()
            delay(250)
        }
        nowMs = TvRemoteRepository.now()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.colors.background),
    ) {
        Backdrop(imageUrl = media?.background ?: media?.poster)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = NuvioTokens.Space.s20),
        ) {
            TopBar(
                tvName = tvName,
                connection = session.connection,
                playing = session.isPlaybackActive,
                onBack = onBack,
            )

            when {
                session.connection == TvConnectionStatus.Unauthorized -> CenteredMessage(
                    icon = Icons.Rounded.ErrorOutline,
                    title = stringResource(Res.string.tv_remote_status_unauthorized),
                    actionText = stringResource(Res.string.tv_remote_repair),
                    onAction = { TvRemoteNavigation.open(TvRemoteDestination.Devices) },
                )
                media == null -> CenteredMessage(
                    icon = Icons.Rounded.Tv,
                    title = stringResource(Res.string.tv_remote_nothing_playing),
                    message = stringResource(Res.string.tv_remote_nothing_playing_hint),
                )
                else -> NowPlaying(
                    session = session,
                    media = media,
                    positionMs = session.positionAt(nowMs),
                    onOpenSubtitles = { openSheet = TrackSheet.Subtitles },
                    onOpenAudio = { openSheet = TrackSheet.Audio },
                )
            }
        }
    }

    when (openSheet) {
        TrackSheet.Subtitles -> TvSubtitleSheet(
            playback = playback,
            onDismiss = { openSheet = null },
        )
        TrackSheet.Audio -> TvAudioSheet(
            playback = playback,
            onDismiss = { openSheet = null },
        )
        null -> Unit
    }
}

@Composable
private fun Backdrop(imageUrl: String?) {
    val background = MaterialTheme.nuvio.colors.background
    AnimatedContent(
        targetState = imageUrl,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "tv_remote_backdrop",
        modifier = Modifier.fillMaxSize(),
    ) { url ->
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.35f),
            )
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to background.copy(alpha = 0.55f),
                    0.45f to background.copy(alpha = 0.8f),
                    1f to background,
                ),
            ),
    )
}

@Composable
private fun TopBar(tvName: String, connection: TvConnectionStatus, playing: Boolean, onBack: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = NuvioTokens.Space.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NuvioBackButton(
            onClick = onBack,
            containerColor = tokens.colors.background.copy(alpha = 0.45f),
        )
        Spacer(Modifier.size(NuvioTokens.Space.s12))
        if (tvName.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .clip(tokens.shapes.chip)
                    .background(tokens.colors.surfaceCard.copy(alpha = 0.7f))
                    .padding(horizontal = NuvioTokens.Space.s12, vertical = NuvioTokens.Space.s6),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
            ) {
                ConnectionDot(connection)
                Text(
                    text = stringResource(
                        if (playing) Res.string.tv_remote_playing_on else Res.string.tv_remote_connected_to,
                        tvName,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = tokens.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun NowPlaying(
    session: TvSessionState,
    media: RemoteMedia,
    positionMs: Long,
    onOpenSubtitles: () -> Unit,
    onOpenAudio: () -> Unit,
) {
    val playback = session.playback
    val tokens = MaterialTheme.nuvio
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compact = maxHeight < 760.dp
        val posterWidth = (maxHeight * 0.22f).coerceIn(110.dp, 200.dp)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(if (compact) NuvioTokens.Space.s8 else NuvioTokens.Space.s24))
            // Poster takes what the controls leave over, so volume stays on screen.
            Poster(url = media.poster ?: media.background, width = posterWidth)
            Spacer(Modifier.height(NuvioTokens.Space.s20))
            TitleBlock(media)
            Spacer(Modifier.height(NuvioTokens.Space.s8))
            StatusLine(session)
            Spacer(Modifier.height(if (compact) NuvioTokens.Space.s16 else NuvioTokens.Space.s28))

            SeekBar(
                positionMs = positionMs,
                durationMs = playback.durationMs,
                enabled = session.connection == TvConnectionStatus.Connected && playback.durationMs > 0L,
            )
            Spacer(Modifier.height(NuvioTokens.Space.s12))
            TransportControls(session)
            Spacer(Modifier.height(NuvioTokens.Space.s20))
            SecondaryControls(
                hasNext = playback.hasNext,
                onOpenSubtitles = onOpenSubtitles,
                onOpenAudio = onOpenAudio,
            )
            Spacer(Modifier.height(NuvioTokens.Space.s20))
            VolumeControls(session)
            Spacer(Modifier.height(NuvioTokens.Space.s24))
        }
    }
}

@Composable
private fun Poster(url: String?, width: Dp) {
    val tokens = MaterialTheme.nuvio
    Box(
        modifier = Modifier
            .widthIn(max = width)
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .clip(tokens.shapes.poster)
            .background(tokens.colors.surfaceCard),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Tv,
                contentDescription = null,
                tint = tokens.colors.textMuted,
                modifier = Modifier.size(48.dp),
            )
        }
    }
}

@Composable
private fun TitleBlock(media: RemoteMedia) {
    val tokens = MaterialTheme.nuvio
    Text(
        text = media.title,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = tokens.colors.textPrimary,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    val season = media.season
    val episode = media.episode
    if (season != null && episode != null) {
        val label = stringResource(Res.string.tv_remote_episode_label, season, episode)
        Text(
            text = listOfNotNull(label, media.episodeTitle?.takeIf { it.isNotBlank() }).joinToString(" · "),
            style = MaterialTheme.typography.bodyLarge,
            color = tokens.colors.textSecondary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = NuvioTokens.Space.s4),
        )
    }
}

@Composable
private fun StatusLine(session: TvSessionState) {
    val tokens = MaterialTheme.nuvio
    val status = session.playback.status
    val notConnected = session.lastError == "not_connected"
    val text = when {
        notConnected -> stringResource(Res.string.tv_remote_error_not_connected)
        status == RemotePlaybackStatus.Loading -> stringResource(Res.string.tv_remote_loading)
        status == RemotePlaybackStatus.Buffering -> stringResource(Res.string.tv_remote_buffering)
        status == RemotePlaybackStatus.Ended -> stringResource(Res.string.tv_remote_ended)
        status == RemotePlaybackStatus.Error ->
            session.playback.error ?: stringResource(Res.string.tv_remote_error_playback)
        else -> null
    }
    Box(modifier = Modifier.height(36.dp), contentAlignment = Alignment.Center) {
        if (text == null) return@Box
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
        ) {
            val isError = notConnected || status == RemotePlaybackStatus.Error
            if (status == RemotePlaybackStatus.Loading || status == RemotePlaybackStatus.Buffering) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = tokens.colors.accent,
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isError) tokens.colors.danger else tokens.colors.textMuted,
            )
            if (isError) {
                Text(
                    text = stringResource(Res.string.tv_remote_retry),
                    style = MaterialTheme.typography.labelLarge,
                    color = tokens.colors.accent,
                    modifier = Modifier
                        .clip(tokens.shapes.chip)
                        .clickable { TvRemoteRepository.reconnect() }
                        .padding(horizontal = NuvioTokens.Space.s8, vertical = NuvioTokens.Space.s4),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeekBar(positionMs: Long, durationMs: Long, enabled: Boolean) {
    val tokens = MaterialTheme.nuvio
    // While dragging, the bar shows the target time; the seek is sent on release only.
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val fraction = when {
        dragFraction != null -> dragFraction!!
        durationMs > 0L -> (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
        else -> 0f
    }
    val shownPosition = dragFraction?.let { (it * durationMs).toLong() } ?: positionMs

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.Center) {
            if (dragFraction != null) {
                Text(
                    text = formatPlaybackTime(shownPosition),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = tokens.colors.accent,
                )
            }
        }
        Slider(
            value = fraction,
            onValueChange = { dragFraction = it },
            onValueChangeFinished = {
                dragFraction?.let { TvRemoteRepository.seekTo((it * durationMs).toLong()) }
                dragFraction = null
            },
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = tokens.colors.playerTimelineFill,
                activeTrackColor = tokens.colors.playerTimelineFill,
                inactiveTrackColor = tokens.colors.playerTimelineTrack,
                disabledThumbColor = tokens.colors.textMuted,
                disabledActiveTrackColor = tokens.colors.textMuted,
                disabledInactiveTrackColor = tokens.colors.playerTimelineTrack,
            ),
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = formatPlaybackTime(shownPosition),
                style = MaterialTheme.typography.labelMedium,
                color = tokens.colors.textSecondary,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (durationMs > 0L) "-" + formatPlaybackTime(durationMs - shownPosition) else "--:--",
                style = MaterialTheme.typography.labelMedium,
                color = tokens.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun TransportControls(session: TvSessionState) {
    val tokens = MaterialTheme.nuvio
    val status = session.playback.status
    val connected = session.connection == TvConnectionStatus.Connected
    val isPlaying = status == RemotePlaybackStatus.Playing || status == RemotePlaybackStatus.Buffering
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIconButton(
            icon = Icons.Rounded.Replay10,
            contentDescription = stringResource(Res.string.tv_remote_rewind, SKIP_SECONDS),
            size = 56.dp,
            enabled = connected,
            onClick = { TvRemoteRepository.seekBy(-SKIP_SECONDS * 1000L) },
        )
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(if (connected) tokens.colors.accent else tokens.colors.accent.copy(alpha = 0.4f))
                .clickable(enabled = connected) { TvRemoteRepository.togglePause() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = stringResource(
                    if (isPlaying) Res.string.tv_remote_pause else Res.string.tv_remote_play,
                ),
                tint = tokens.colors.onAccent,
                modifier = Modifier.size(44.dp),
            )
        }
        RoundIconButton(
            icon = Icons.Rounded.Forward10,
            contentDescription = stringResource(Res.string.tv_remote_forward, SKIP_SECONDS),
            size = 56.dp,
            enabled = connected,
            onClick = { TvRemoteRepository.seekBy(SKIP_SECONDS * 1000L) },
        )
    }
}

@Composable
private fun SecondaryControls(
    hasNext: Boolean,
    onOpenSubtitles: () -> Unit,
    onOpenAudio: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        LabeledAction(
            icon = Icons.Rounded.ClosedCaption,
            label = stringResource(Res.string.tv_remote_subtitles),
            onClick = onOpenSubtitles,
        )
        LabeledAction(
            icon = Icons.Rounded.Audiotrack,
            label = stringResource(Res.string.tv_remote_audio),
            onClick = onOpenAudio,
        )
        LabeledAction(
            icon = Icons.Rounded.SkipNext,
            label = stringResource(Res.string.tv_remote_next_episode),
            enabled = hasNext,
            onClick = TvRemoteRepository::playNext,
        )
        LabeledAction(
            icon = Icons.Rounded.Stop,
            label = stringResource(Res.string.tv_remote_stop),
            onClick = TvRemoteRepository::stop,
        )
    }
}

@Composable
private fun VolumeControls(session: TvSessionState) {
    val tokens = MaterialTheme.nuvio
    val volume = session.volume
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(NuvioTokens.Space.s28))
            .background(tokens.colors.surfaceCard.copy(alpha = 0.75f))
            .padding(horizontal = NuvioTokens.Space.s8, vertical = NuvioTokens.Space.s6),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIconButton(
            icon = Icons.AutoMirrored.Rounded.VolumeDown,
            contentDescription = stringResource(Res.string.tv_remote_volume_down),
            size = 48.dp,
            transparent = true,
            onClick = TvRemoteRepository::volumeDown,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(tokens.shapes.chip)
                .clickable(onClick = TvRemoteRepository::toggleMute)
                .padding(vertical = NuvioTokens.Space.s4),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
            ) {
                Icon(
                    imageVector = if (volume.muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                    contentDescription = stringResource(
                        if (volume.muted) Res.string.tv_remote_unmute else Res.string.tv_remote_mute,
                    ),
                    tint = if (volume.muted) tokens.colors.danger else tokens.colors.textPrimary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = volume.level?.toString() ?: "–",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.colors.textPrimary,
                )
            }
            Text(
                text = stringResource(Res.string.tv_remote_volume),
                style = MaterialTheme.typography.labelSmall,
                color = tokens.colors.textMuted,
            )
        }
        RoundIconButton(
            icon = Icons.AutoMirrored.Rounded.VolumeUp,
            contentDescription = stringResource(Res.string.tv_remote_volume_up),
            size = 48.dp,
            transparent = true,
            onClick = TvRemoteRepository::volumeUp,
        )
    }
}

@Composable
private fun RoundIconButton(
    icon: ImageVector,
    contentDescription: String,
    size: Dp,
    enabled: Boolean = true,
    transparent: Boolean = false,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (transparent) Color.Transparent else tokens.colors.surfaceCard.copy(alpha = 0.75f))
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tokens.colors.textPrimary,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

@Composable
private fun LabeledAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    Column(
        modifier = Modifier
            .widthIn(min = 72.dp, max = 84.dp)
            .clip(tokens.shapes.compactCard)
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.35f)
            .padding(vertical = NuvioTokens.Space.s8),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(tokens.colors.surfaceCard.copy(alpha = 0.75f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tokens.colors.textPrimary,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tokens.colors.textSecondary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CenteredMessage(
    icon: ImageVector,
    title: String,
    message: String? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val tokens = MaterialTheme.nuvio
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = NuvioTokens.Space.s24),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tokens.colors.textMuted,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(NuvioTokens.Space.s16))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = tokens.colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        if (message != null) {
            Spacer(Modifier.height(NuvioTokens.Space.s8))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.colors.textMuted,
                textAlign = TextAlign.Center,
            )
        }
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(NuvioTokens.Space.s24))
            NuvioPrimaryButton(
                text = actionText,
                onClick = onAction,
                modifier = Modifier.widthIn(max = 280.dp),
            )
        }
        Spacer(Modifier.height(80.dp))
    }
}
