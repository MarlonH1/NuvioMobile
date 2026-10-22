package com.nuvio.app.features.tvremote.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.rounded.SettingsRemote
import com.nuvio.app.features.extras.ExtrasSettingsRepository
import com.nuvio.app.features.extras.RemoteButtonId
import com.nuvio.app.features.extras.normalizedRemoteButtons
import nuvio.composeapp.generated.resources.tv_remote_full_remote
import nuvio.composeapp.generated.resources.tv_remote_open_remote
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
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
import com.nuvio.app.features.tvremote.protocol.RemoteSkipKind
import com.nuvio.app.features.tvremote.protocol.RemoteSkipSegment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.graphics.lerp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_back
import nuvio.composeapp.generated.resources.player_skip_intro
import nuvio.composeapp.generated.resources.player_skip_movie_credits
import nuvio.composeapp.generated.resources.player_skip_outro
import nuvio.composeapp.generated.resources.player_skip_recap
import nuvio.composeapp.generated.resources.player_skip_to_post_credits
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
import org.jetbrains.compose.resources.stringResource

/** Matches the hard-coded ±10 s of the phone player (PlayerScreenRuntimeUi). */
private const val SKIP_SECONDS = 10
private const val MAX_TV_VOLUME = 100
private val RemoteBase = Color(0xFF07080A)
private val OnWhite = Color(0xFF0B0C0F)

private enum class TrackSheet { Subtitles, Audio }

@Composable
fun TvRemoteControlScreen(onBack: () -> Unit) {
    LaunchedEffect(Unit) { TvRemoteRepository.ensureLoaded() }
    val session by TvRemoteRepository.session.collectAsStateWithLifecycle()
    val tvs by TvRemoteRepository.pairedTvs.collectAsStateWithLifecycle()
    val tvName = tvs.firstOrNull { it.id == session.tvId }?.name.orEmpty()
    val playback = session.playback
    val media = playback.media
    var openSheet by remember { mutableStateOf<TrackSheet?>(null) }
    var partySheetOpen by remember { mutableStateOf(false) }
    var fullRemoteOpen by remember { mutableStateOf(false) }
    val party = session.party?.takeIf { session.connection == TvConnectionStatus.Connected }
    // Otherwise the sheet would pop open by itself when the next party arrives.
    LaunchedEffect(party == null) { if (party == null) partySheetOpen = false }

    // The poster's painter feeds the ambient color once it has loaded.
    var posterPainter by remember(media?.poster) { mutableStateOf<Painter?>(null) }
    val ambient = rememberAmbientColor(posterPainter, fallbackKey = media?.title ?: tvName)

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
            .background(RemoteBase),
    ) {
        AmbientBackdrop(imageUrl = media?.background ?: media?.poster, ambient = ambient, active = media != null)

        Column(
            modifier = Modifier
                .fillMaxSize()
                // No bottom inset here: the controls scroll under the home indicator,
                // NowPlaying pads its scroll content instead.
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(horizontal = NuvioTokens.Space.s20),
        ) {
            TopBar(
                tvName = tvName,
                connection = session.connection,
                playing = session.isPlaybackActive,
                onBack = onBack,
            )
            if (party != null) {
                TvPartyStrip(
                    party = party,
                    onOpen = { partySheetOpen = true },
                    modifier = Modifier.padding(bottom = NuvioTokens.Space.s8),
                )
            }

            Box(modifier = Modifier.weight(1f)) {
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
                        actionText = stringResource(Res.string.tv_remote_open_remote).takeIf {
                            session.connection == TvConnectionStatus.Connected
                        },
                        onAction = { fullRemoteOpen = true },
                    )
                    else -> NowPlaying(
                        session = session,
                        media = media,
                        ambient = ambient,
                        positionMs = session.positionAt(nowMs),
                        onPosterLoaded = { posterPainter = it },
                        onOpenSubtitles = { openSheet = TrackSheet.Subtitles },
                        onOpenAudio = { openSheet = TrackSheet.Audio },
                        onOpenRemote = { fullRemoteOpen = true },
                    )
                }
            }
        }
    }

    if (fullRemoteOpen) TvFullRemoteSheet(ambient = ambient, onDismiss = { fullRemoteOpen = false })
    when (openSheet) {
        TrackSheet.Subtitles -> TvSubtitleSheet(playback = playback, ambient = ambient, onDismiss = { openSheet = null })
        TrackSheet.Audio -> TvAudioSheet(playback = playback, ambient = ambient, onDismiss = { openSheet = null })
        null -> Unit
    }
    if (partySheetOpen && party != null) {
        TvPartySheet(
            party = party,
            invites = session.partyInvites,
            ambient = ambient,
            onDismiss = { partySheetOpen = false },
        )
    }
}

@Composable
private fun AmbientBackdrop(imageUrl: String?, ambient: Color, active: Boolean) {
    val glowAlpha by animateFloatAsState(if (active) 1f else 0.4f, label = "tv_remote_backdrop_alpha")
    // Blurred artwork for texture (blur is a no-op before Android 12; the gradients carry the look).
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
                    .blur(48.dp)
                    .alpha(0.45f),
            )
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(glowAlpha)
            .drawBehind {
                val radius = size.maxDimension * 0.75f
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(ambient.copy(alpha = 0.6f), ambient.copy(alpha = 0.14f), Color.Transparent),
                        center = Offset(size.width / 2f, size.height * 0.22f),
                        radius = radius,
                    ),
                )
            },
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to RemoteBase.copy(alpha = 0.1f),
                    0.45f to RemoteBase.copy(alpha = 0.5f),
                    1f to RemoteBase.copy(alpha = 0.96f),
                ),
            ),
    )
}

@Composable
private fun TopBar(tvName: String, connection: TvConnectionStatus, playing: Boolean, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = NuvioTokens.Space.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassIconButton(
            icon = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = stringResource(Res.string.action_back),
            size = 44.dp,
            onClick = onBack,
        )
        Spacer(Modifier.weight(1f))
        if (tvName.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .tvGlass(CircleShape)
                    .padding(start = NuvioTokens.Space.s4, end = NuvioTokens.Space.s14, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PulsingDot(
                    color = connectionColor(connection),
                    pulsing = playing && connection == TvConnectionStatus.Connected,
                )
                Text(
                    text = stringResource(
                        if (playing) Res.string.tv_remote_playing_on else Res.string.tv_remote_connected_to,
                        tvName,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        // Balances the back button so the chip stays centered.
        Spacer(Modifier.size(44.dp))
    }
}

@Composable
private fun NowPlaying(
    session: TvSessionState,
    media: RemoteMedia,
    ambient: Color,
    positionMs: Long,
    onPosterLoaded: (Painter) -> Unit,
    onOpenSubtitles: () -> Unit,
    onOpenAudio: () -> Unit,
    onOpenRemote: () -> Unit,
) {
    val playback = session.playback
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compact = maxHeight < 760.dp
        val posterWidth = (maxHeight * 0.2f).coerceIn(110.dp, 190.dp)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(if (compact) NuvioTokens.Space.s8 else NuvioTokens.Space.s16))
            Poster(
                url = media.poster ?: media.background,
                title = media.title,
                ambient = ambient,
                width = posterWidth,
                dimmed = playback.status == RemotePlaybackStatus.Paused,
                onLoaded = onPosterLoaded,
            )
            Spacer(Modifier.height(NuvioTokens.Space.s20))
            TitleBlock(media)
            StatusLine(session)
            Spacer(Modifier.height(if (compact) NuvioTokens.Space.s4 else NuvioTokens.Space.s12))

            SeekBar(
                positionMs = positionMs,
                durationMs = playback.durationMs,
                ambient = ambient,
                enabled = session.connection == TvConnectionStatus.Connected && playback.durationMs > 0L,
            )
            // Keeps the last segment while the button animates out after a skip.
            var lastSkip by remember { mutableStateOf<RemoteSkipSegment?>(null) }
            playback.skip?.let { lastSkip = it }
            AnimatedVisibility(
                visible = playback.skip != null && session.connection == TvConnectionStatus.Connected,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                lastSkip?.let { SkipSegmentButton(it) }
            }
            Spacer(Modifier.height(NuvioTokens.Space.s12))
            TransportControls(session, ambient)
            Spacer(Modifier.height(NuvioTokens.Space.s12))
            VolumeControls(session, ambient)
            Spacer(Modifier.height(NuvioTokens.Space.s12))
            SecondaryControls(
                subtitlesOn = playback.subtitleTracks.any { it.selected },
                hasNext = playback.hasNext,
                rate = playback.rate,
                onOpenSubtitles = onOpenSubtitles,
                onOpenAudio = onOpenAudio,
                onOpenRemote = onOpenRemote,
            )
            Spacer(Modifier.height(NuvioTokens.Space.s24))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.safeDrawing))
        }
    }
}

@Composable
private fun Poster(
    url: String?,
    title: String,
    ambient: Color,
    width: Dp,
    dimmed: Boolean,
    onLoaded: (Painter) -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val scale by animateFloatAsState(if (dimmed) 0.94f else 1f, label = "tv_remote_poster_scale")
    Box(
        modifier = Modifier
            .widthIn(max = width)
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .scale(scale),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .shadow(28.dp, shape, ambientColor = ambient, spotColor = ambient)
                .clip(shape)
                .border(1.dp, Color.White.copy(alpha = 0.14f), shape),
        ) {
            TvArtworkPlaceholder(title = title, color = ambient)
            if (url != null) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onSuccess = { state -> onLoaded(state.painter) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // Glossy top edge.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.10f),
                            0.25f to Color.Transparent,
                        ),
                    ),
            )
        }
    }
}

@Composable
private fun TitleBlock(media: RemoteMedia) {
    Text(
        text = media.title,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.ExtraBold,
        color = Color.White,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    val season = media.season
    val episode = media.episode
    if (season != null && episode != null) {
        Row(
            modifier = Modifier.padding(top = NuvioTokens.Space.s6),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
        ) {
            Text(
                text = stringResource(Res.string.tv_remote_episode_label, season, episode),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .tvGlass(RoundedCornerShape(6.dp))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            )
            media.episodeTitle?.takeIf { it.isNotBlank() }?.let { episodeTitle ->
                Text(
                    text = episodeTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
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
    // Takes no space while there is nothing to report, so the seek bar sits right under the title.
    AnimatedVisibility(
        visible = text != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Box(modifier = Modifier.height(44.dp), contentAlignment = Alignment.Center) {
            if (text == null) return@Box
            val isError = notConnected || status == RemotePlaybackStatus.Error
            Row(
                modifier = Modifier
                    .tvGlass(CircleShape, tint = if (isError) tokens.colors.danger else Color.White)
                    .padding(horizontal = NuvioTokens.Space.s12, vertical = NuvioTokens.Space.s6),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
            ) {
                if (status == RemotePlaybackStatus.Loading || status == RemotePlaybackStatus.Buffering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = Color.White,
                    )
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.9f),
                )
                if (isError) {
                    Text(
                        text = stringResource(Res.string.tv_remote_retry),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.clickable { TvRemoteRepository.reconnect() },
                    )
                }
            }
        }
    }
}

/**
 * Slim custom timeline. Dragging or tapping previews the target time in a bubble;
 * the seek is only sent on release.
 */
@Composable
private fun SeekBar(positionMs: Long, durationMs: Long, ambient: Color, enabled: Boolean) {
    var widthPx by remember { mutableFloatStateOf(1f) }
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val currentDuration by rememberUpdatedState(durationMs)
    val currentEnabled by rememberUpdatedState(enabled)
    val playedFraction = dragFraction
        ?: if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val shownPosition = dragFraction?.let { (it * durationMs).toLong() } ?: positionMs
    val dragging = dragFraction != null
    val density = LocalDensity.current

    fun commit() {
        dragFraction?.let { TvRemoteRepository.seekTo((it * currentDuration).toLong()) }
        dragFraction = null
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            if (currentEnabled) dragFraction = (offset.x / widthPx).coerceIn(0f, 1f)
                        },
                        onHorizontalDrag = { change, _ ->
                            if (dragFraction != null) dragFraction = (change.position.x / widthPx).coerceIn(0f, 1f)
                        },
                        onDragEnd = { commit() },
                        onDragCancel = { dragFraction = null },
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        if (currentEnabled) {
                            dragFraction = (offset.x / widthPx).coerceIn(0f, 1f)
                            commit()
                        }
                    }
                },
            contentAlignment = Alignment.BottomStart,
        ) {
            // Time bubble that follows the thumb while scrubbing.
            if (dragging) {
                val bubbleWidth = 78.dp
                val trackWidth = with(density) { widthPx.toDp() }
                val bubbleX = (trackWidth * playedFraction - bubbleWidth / 2)
                    .coerceIn(0.dp, (trackWidth - bubbleWidth).coerceAtLeast(0.dp))
                Text(
                    text = formatPlaybackTime(shownPosition),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = OnWhite,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        // Floats above the bar so it needs no reserved space.
                        .offset { IntOffset(bubbleX.roundToPx(), (-30).dp.roundToPx()) }
                        .width(bubbleWidth)
                        .shadow(8.dp, CircleShape)
                        .background(Color.White, CircleShape)
                        .padding(vertical = 5.dp),
                )
            }
            GlowTrack(fraction = playedFraction, ambient = ambient, dragging = dragging, enabled = enabled)
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = formatPlaybackTime(shownPosition),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.85f),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (durationMs > 0L) "-" + formatPlaybackTime(durationMs - shownPosition) else "--:--",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.55f),
            )
        }
    }
}

/** Track + thumb of the seek bar. */
@Composable
private fun GlowTrack(fraction: Float, ambient: Color, dragging: Boolean, enabled: Boolean) {
    val trackHeight by animateDpAsState(if (dragging) 8.dp else 5.dp, label = "tv_remote_track")
    val thumbSize by animateDpAsState(if (dragging) 22.dp else 14.dp, label = "tv_remote_thumb")
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .alpha(if (enabled) 1f else 0.45f),
    ) {
        val track = trackHeight.toPx()
        val y = size.height / 2f
        val radius = CornerRadius(track / 2f)
        drawRoundRect(
            color = Color.White.copy(alpha = 0.16f),
            topLeft = Offset(0f, y - track / 2f),
            size = Size(size.width, track),
            cornerRadius = radius,
        )
        val playedWidth = size.width * fraction
        drawRoundRect(
            brush = Brush.horizontalGradient(
                listOf(ambient, Color.White),
                startX = 0f,
                endX = playedWidth.coerceAtLeast(1f),
            ),
            topLeft = Offset(0f, y - track / 2f),
            size = Size(playedWidth, track),
            cornerRadius = radius,
        )
        val thumb = thumbSize.toPx()
        drawCircle(color = ambient.copy(alpha = 0.35f), radius = thumb, center = Offset(playedWidth, y))
        drawCircle(color = Color.White, radius = thumb / 2f, center = Offset(playedWidth, y))
    }
}

@Composable
private fun TransportControls(session: TvSessionState, ambient: Color) {
    val status = session.playback.status
    val connected = session.connection == TvConnectionStatus.Connected
    val isPlaying = status == RemotePlaybackStatus.Playing || status == RemotePlaybackStatus.Buffering
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val playScale by animateFloatAsState(if (pressed) 0.92f else 1f, label = "tv_remote_play_scale")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassIconButton(
            icon = Icons.Rounded.Replay10,
            contentDescription = stringResource(Res.string.tv_remote_rewind, SKIP_SECONDS),
            size = 60.dp,
            enabled = connected,
            onClick = { TvRemoteRepository.seekBy(-SKIP_SECONDS * 1000L) },
        )
        Box(
            modifier = Modifier
                .size(104.dp)
                .tvGlow(ambient, radiusFraction = 0.6f, alpha = if (connected) 0.75f else 0.2f),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .scale(playScale)
                    .shadow(18.dp, CircleShape, ambientColor = ambient, spotColor = ambient)
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFE2E4EA))))
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        enabled = connected,
                    ) { TvRemoteRepository.togglePause() }
                    .alpha(if (connected) 1f else 0.5f),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = isPlaying,
                    transitionSpec = { (scaleIn() + fadeIn()) togetherWith (scaleOut() + fadeOut()) },
                    label = "tv_remote_play_icon",
                ) { playing ->
                    Icon(
                        imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(
                            if (playing) Res.string.tv_remote_pause else Res.string.tv_remote_play,
                        ),
                        tint = OnWhite,
                        modifier = Modifier.size(46.dp),
                    )
                }
            }
        }
        GlassIconButton(
            icon = Icons.Rounded.Forward10,
            contentDescription = stringResource(Res.string.tv_remote_forward, SKIP_SECONDS),
            size = 60.dp,
            enabled = connected,
            onClick = { TvRemoteRepository.seekBy(SKIP_SECONDS * 1000L) },
        )
    }
}

@Composable
private fun SkipSegmentButton(segment: RemoteSkipSegment) {
    val label = stringResource(
        when {
            segment.postCredits -> Res.string.player_skip_to_post_credits
            segment.kind == RemoteSkipKind.Recap -> Res.string.player_skip_recap
            segment.kind == RemoteSkipKind.Outro -> Res.string.player_skip_outro
            segment.kind == RemoteSkipKind.Credits -> Res.string.player_skip_movie_credits
            else -> Res.string.player_skip_intro
        },
    )
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, label = "tv_remote_skip_scale")
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = NuvioTokens.Space.s12),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .scale(scale)
                .clip(RoundedCornerShape(50))
                .background(Color.White)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = TvRemoteRepository::skipSegment,
                )
                .padding(horizontal = NuvioTokens.Space.s20, vertical = NuvioTokens.Space.s12),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipNext,
                contentDescription = null,
                tint = OnWhite,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = OnWhite,
            )
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SecondaryControls(
    subtitlesOn: Boolean,
    hasNext: Boolean,
    rate: Float,
    onOpenSubtitles: () -> Unit,
    onOpenAudio: () -> Unit,
    onOpenRemote: () -> Unit,
) {
    var speedMenuOpen by remember { mutableStateOf(false) }
    val extras by ExtrasSettingsRepository.state.collectAsStateWithLifecycle()
    val buttons = normalizedRemoteButtons(extras.remoteButtons).filter { it.enabled }
    if (buttons.isEmpty()) return
    // Wraps to a second line instead of squeezing when many buttons are on.
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .tvGlass(RoundedCornerShape(24.dp))
            .padding(vertical = NuvioTokens.Space.s4),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        buttons.forEach { entry ->
            when (entry.id) {
                RemoteButtonId.Subtitles -> LabeledAction(
                    icon = Icons.Rounded.ClosedCaption,
                    label = stringResource(Res.string.tv_remote_subtitles),
                    active = subtitlesOn,
                    onClick = onOpenSubtitles,
                )
                RemoteButtonId.Audio -> LabeledAction(
                    icon = Icons.Rounded.Audiotrack,
                    label = stringResource(Res.string.tv_remote_audio),
                    onClick = onOpenAudio,
                )
                RemoteButtonId.Speed -> Box {
                    LabeledAction(
                        icon = Icons.Rounded.Speed,
                        label = formatRemoteRate(rate),
                        active = kotlin.math.abs(rate - 1f) > 0.01f,
                        onClick = { speedMenuOpen = true },
                    )
                    DropdownMenu(expanded = speedMenuOpen, onDismissRequest = { speedMenuOpen = false }) {
                        REMOTE_SPEEDS.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        formatRemoteRate(option),
                                        fontWeight = if (kotlin.math.abs(option - rate) < 0.01f) FontWeight.Bold else FontWeight.Normal,
                                    )
                                },
                                onClick = {
                                    speedMenuOpen = false
                                    TvRemoteRepository.setSpeed(option)
                                },
                            )
                        }
                    }
                }
                RemoteButtonId.Remote -> LabeledAction(
                    icon = Icons.Rounded.SettingsRemote,
                    label = stringResource(Res.string.tv_remote_full_remote),
                    onClick = onOpenRemote,
                )
                RemoteButtonId.Next -> LabeledAction(
                    icon = Icons.Rounded.SkipNext,
                    label = stringResource(Res.string.tv_remote_next_episode),
                    enabled = hasNext,
                    onClick = TvRemoteRepository::playNext,
                )
                RemoteButtonId.Exit -> LabeledAction(
                    icon = Icons.AutoMirrored.Rounded.ExitToApp,
                    label = stringResource(Res.string.tv_remote_stop),
                    onClick = TvRemoteRepository::stop,
                )
            }
        }
    }
}

// Speeds the TV player offers (webOS native playback tops out at 2x).
private val REMOTE_SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

private fun formatRemoteRate(rate: Float): String {
    val hundredths = kotlin.math.round(rate * 100).toInt()
    val fraction = (hundredths % 100).toString().padStart(2, '0').trimEnd('0')
    return if (fraction.isEmpty()) "${hundredths / 100}x" else "${hundredths / 100}.${fraction}x"
}

/**
 * Control Center style volume pill. Drags change the level relative to where the finger
 * started, so touching it never jumps; the speaker toggles mute. Levels are sent live while
 * dragging, TvRemoteRepository.setVolume coalesces them.
 */
@Composable
private fun VolumeControls(session: TvSessionState, ambient: Color) {
    val tokens = MaterialTheme.nuvio
    val haptics = LocalHapticFeedback.current
    val volume = session.volume
    val level = volume.level
    val currentLevel by rememberUpdatedState(level)
    var widthPx by remember { mutableFloatStateOf(1f) }
    var dragging by remember { mutableStateOf(false) }
    var dragLevel by remember { mutableFloatStateOf(0f) }
    val target = ((level ?: 0).toFloat() / MAX_TV_VOLUME).coerceIn(0f, 1f)
    // Follows the finger exactly while dragging, eases when the TV reports a change.
    val animated by animateFloatAsState(target, label = "tv_remote_volume_fill")
    val fraction = if (dragging) target else animated
    val scale by animateFloatAsState(if (dragging) 1.03f else 1f, label = "tv_remote_volume_scale")
    val label = stringResource(Res.string.tv_remote_volume)
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .tvGlass(shape)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo((level ?: 0).toFloat(), 0f..MAX_TV_VOLUME.toFloat())
                setProgress { value ->
                    TvRemoteRepository.setVolume(value.roundToInt())
                    true
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        currentLevel?.let {
                            dragLevel = it.toFloat()
                            dragging = true
                        }
                    },
                    onHorizontalDrag = { change, dx ->
                        if (dragging) {
                            change.consume()
                            val before = dragLevel.roundToInt()
                            dragLevel = (dragLevel + dx / widthPx * MAX_TV_VOLUME).coerceIn(0f, MAX_TV_VOLUME.toFloat())
                            val after = dragLevel.roundToInt()
                            if (after != before) {
                                TvRemoteRepository.setVolume(after)
                                if (after / 10 != before / 10 || after == 0 || after == MAX_TV_VOLUME) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        }
                    },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                )
            },
    ) {
        // Tinted glass: the poster color as a translucent pane, lit along the top edge.
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction)
                .alpha(if (volume.muted) 0.3f else 1f)
                .background(lerp(Color.White, ambient, 0.55f).copy(alpha = 0.66f))
                .drawBehind {
                    drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.45f), 0.08f to Color.Transparent))
                    drawRect(Brush.verticalGradient(0.6f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.12f)))
                },
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = NuvioTokens.Space.s8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(onClick = TvRemoteRepository::toggleMute),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = when {
                        volume.muted -> Icons.AutoMirrored.Rounded.VolumeOff
                        (level ?: 0) < MAX_TV_VOLUME / 3 -> Icons.AutoMirrored.Rounded.VolumeDown
                        else -> Icons.AutoMirrored.Rounded.VolumeUp
                    },
                    contentDescription = stringResource(
                        if (volume.muted) Res.string.tv_remote_unmute else Res.string.tv_remote_mute,
                    ),
                    tint = if (volume.muted) tokens.colors.danger else Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    size: Dp,
    enabled: Boolean = true,
    flat: Boolean = false,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, label = "tv_remote_button_scale")
    Box(
        modifier = Modifier
            .size(size)
            .scale(scale)
            .then(if (flat) Modifier.clip(CircleShape) else Modifier.tvGlass(CircleShape))
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.35f),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(size * 0.48f),
        )
    }
}

@Composable
private fun LabeledAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .widthIn(min = 72.dp, max = 84.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.3f)
            .padding(vertical = NuvioTokens.Space.s8),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s4),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (active) Color.White else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (active) OnWhite else Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            color = Color.White.copy(alpha = if (active) 1f else 0.7f),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontSize = 11.sp,
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
    val glow = remoteAccent()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = NuvioTokens.Space.s24),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .tvGlow(glow, radiusFraction = 0.55f, alpha = 0.22f),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .tvGlass(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(40.dp),
                )
            }
        }
        Spacer(Modifier.height(NuvioTokens.Space.s20))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        if (message != null) {
            Spacer(Modifier.height(NuvioTokens.Space.s8))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 320.dp),
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
