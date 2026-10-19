package com.nuvio.app.features.tvremote.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kmpalette.extensions.painter.rememberPainterDominantColorState
import com.nuvio.app.core.ui.nuvio
import kotlin.math.abs

/** Frosted surface used for all remote controls: faint fill plus a hairline edge. */
internal fun Modifier.tvGlass(shape: Shape, selected: Boolean = false, tint: Color = Color.White): Modifier =
    this
        .clip(shape)
        .background(tint.copy(alpha = if (selected) 0.22f else 0.07f), shape)
        .border(1.dp, Color.White.copy(alpha = if (selected) 0.28f else 0.10f), shape)

/** Soft radial light, e.g. behind the poster or the play button. */
internal fun Modifier.tvGlow(color: Color, radiusFraction: Float = 0.75f, alpha: Float = 0.55f): Modifier =
    drawBehind {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = alpha), color.copy(alpha = 0f)),
                center = Offset(size.width / 2f, size.height / 2f),
                radius = size.maxDimension * radiusFraction,
            ),
            radius = size.maxDimension * radiusFraction,
        )
    }

/** Stable, pleasant color for a title when no artwork is available. */
internal fun fallbackAmbientColor(key: String): Color {
    val hue = (abs(key.hashCode()) % 360).toFloat()
    return Color.hsv(hue, 0.55f, 0.62f)
}

/**
 * Ambient color for the now-playing media: the poster's dominant color once it has
 * loaded, otherwise a color derived from the title. Animated between changes.
 */
@Composable
internal fun rememberAmbientColor(painter: Painter?, fallbackKey: String): Color {
    val fallback = fallbackAmbientColor(fallbackKey)
    val state = rememberPainterDominantColorState(defaultColor = fallback, defaultOnColor = Color.White)
    LaunchedEffect(painter) {
        if (painter != null) runCatching { state.updateFrom(painter) }
    }
    val target = if (painter != null) state.color else fallback
    val animated by animateColorAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 600, easing = LinearOutSlowInEasing),
        label = "tv_remote_ambient",
    )
    return animated
}

/** Artwork placeholder: gradient in the ambient color with the title's initials. */
@Composable
internal fun TvArtworkPlaceholder(title: String, color: Color, modifier: Modifier = Modifier) {
    val initials = title.split(' ', '-', ':')
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifEmpty { "TV" }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF101114))
            .background(
                Brush.linearGradient(
                    listOf(color.copy(alpha = 0.95f), color.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.6f)),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        val fontSize = with(LocalDensity.current) { (maxWidth * 0.28f).toSp() }
        Text(
            text = initials,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = fontSize,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
            maxLines = 1,
        )
    }
}

/** Status dot that pulses while [pulsing] (e.g. connected and playing). */
@Composable
internal fun PulsingDot(color: Color, pulsing: Boolean, size: Dp = 8.dp) {
    val transition = rememberInfiniteTransition(label = "tv_remote_pulse")
    val halo by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_400), RepeatMode.Restart),
        label = "tv_remote_pulse_halo",
    )
    Box(modifier = Modifier.size(size * 2.5f), contentAlignment = Alignment.Center) {
        if (pulsing) {
            Box(
                modifier = Modifier
                    .size(size + size * 1.5f * halo)
                    .background(color.copy(alpha = 0.45f * (1f - halo)), CircleShape),
            )
        }
        Box(modifier = Modifier.size(size).background(color, CircleShape))
    }
}

/** Accent that stays readable on the dark remote: theme accent unless it is too dark. */
@Composable
internal fun remoteAccent(): Color = MaterialTheme.nuvio.colors.accent
