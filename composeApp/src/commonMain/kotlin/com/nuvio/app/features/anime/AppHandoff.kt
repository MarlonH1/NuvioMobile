package com.nuvio.app.features.anime

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.hypot
import kotlin.math.max

/**
 * Switching between this app and its companion (Nuvio <-> NuvioCodeineXO, the anime app).
 * The desktop entry point installs a [launcher]; where none is installed the switch is unavailable.
 *
 * Flow: the outgoing app covers its window with the reveal, starts the companion with a ready-file
 * argument, keeps the cover up until the companion has drawn its first frame, then exits. The
 * incoming app starts fully covered and fades the cover away, so the swap reads as one transition.
 */
object AppHandoff {
    interface Launcher {
        /** Shown while waiting, e.g. "Anime Nuvio". */
        val companionName: String
        /** True when the companion is the anime app (sakura cover), false when returning (dark cover). */
        val goingToAnime: Boolean
        /** Starts the companion, or returns null when it isn't installed / couldn't start. */
        fun launch(): Session?
        /** Leaves this app once the companion is up. */
        fun exitThisApp()
    }

    interface Session {
        val isReady: Boolean
        val isAlive: Boolean
    }

    internal enum class Phase { Idle, Covering, Waiting, Failed, Arriving }

    var launcher: Launcher? = null

    /** Set by the entry point when this process was started by the other app's switch. */
    var arrivalReadySignal: (() -> Unit)? = null
    /** Cover color the outgoing app used, so the arriving app continues the same transition. */
    var arrivalCoverIsAnime: Boolean = false

    private val _phase = MutableStateFlow(Phase.Idle)
    internal val phase = _phase.asStateFlow()

    // Bumped once per click. The switch runs keyed on this, not on [phase], because the switch
    // itself updates [phase]; keying on it cancelled the switch before the companion started.
    private val _requests = MutableStateFlow(0)
    internal val requests = _requests.asStateFlow()
    internal var origin: Offset? = null
    internal var originFraction: Offset? = null

    val isAvailable: Boolean get() = launcher != null

    /** Starts the switch; [center] is the button position in root coordinates when known. */
    fun switch(center: Offset? = null, fraction: Offset? = null) {
        if (launcher == null || _phase.value != Phase.Idle) return
        origin = center
        originFraction = fraction
        _phase.value = Phase.Covering
        _requests.value += 1
    }

    internal fun markArrival() {
        if (arrivalReadySignal != null) _phase.value = Phase.Arriving
    }

    internal fun setPhase(next: Phase) {
        _phase.value = next
    }
}

private val HandoffSakura = listOf(Color(0xFFFF7EB6), Color(0xFFA78BFA), Color(0xFF7DD3FC))
private val HandoffInk = listOf(Color(0xFF26262C), Color(0xFF0D0D10), Color(0xFF0D0D10))

/** Wrap the whole app with this (inside the theme) to get the cross-app transition. */
@Composable
fun AppHandoffHost(content: @Composable () -> Unit) {
    val phase by AppHandoff.phase.collectAsStateWithLifecycle()
    val request by AppHandoff.requests.collectAsStateWithLifecycle()
    val reveal = remember { Animatable(0f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Arriving from the other app: start covered, tell it we're up, then fade in.
        val signal = AppHandoff.arrivalReadySignal ?: return@LaunchedEffect
        AppHandoff.markArrival()
        reveal.snapTo(1f)
        alpha.snapTo(1f)
        delay(250)
        runCatching { signal() }
        AppHandoff.arrivalReadySignal = null
        alpha.animateTo(0f, tween(450, easing = LinearOutSlowInEasing))
        AppHandoff.setPhase(AppHandoff.Phase.Idle)
    }

    LaunchedEffect(request) {
        if (request == 0 || AppHandoff.phase.value != AppHandoff.Phase.Covering) return@LaunchedEffect
        val launcher = AppHandoff.launcher ?: run { AppHandoff.setPhase(AppHandoff.Phase.Idle); return@LaunchedEffect }
        alpha.snapTo(1f)
        reveal.snapTo(0f)
        reveal.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
        AppHandoff.setPhase(AppHandoff.Phase.Waiting)
        val session = runCatching { launcher.launch() }.getOrNull()
        var waitedMs = 0
        while (session != null && session.isAlive && !session.isReady && waitedMs < 25_000) {
            delay(100)
            waitedMs += 100
        }
        if (session != null && session.isReady) {
            delay(150)
            launcher.exitThisApp()
            return@LaunchedEffect
        }
        // Companion missing or it didn't come up: say so, then uncover.
        AppHandoff.setPhase(AppHandoff.Phase.Failed)
        delay(2200)
        alpha.animateTo(0f, tween(360))
        AppHandoff.setPhase(AppHandoff.Phase.Idle)
    }

    Box(Modifier.fillMaxSize()) {
        content()
        if (alpha.value > 0f) {
            val toAnime = if (phase == AppHandoff.Phase.Arriving || AppHandoff.arrivalReadySignal != null) {
                AppHandoff.arrivalCoverIsAnime
            } else {
                AppHandoff.launcher?.goingToAnime ?: true
            }
            val colors = if (toAnime) HandoffSakura else HandoffInk
            Canvas(Modifier.fillMaxSize()) {
                val center = AppHandoff.origin
                    ?: AppHandoff.originFraction?.let { Offset(it.x * size.width, it.y * size.height) }
                    ?: Offset(size.width / 2f, size.height / 2f)
                val maxRadius = listOf(
                    Offset.Zero, Offset(size.width, 0f), Offset(0f, size.height), Offset(size.width, size.height),
                ).maxOf { hypot(it.x - center.x, it.y - center.y) }
                val radius = max(1f, maxRadius * reveal.value)
                drawCircle(
                    brush = Brush.radialGradient(colors, center = center, radius = radius),
                    radius = radius,
                    center = center,
                    alpha = alpha.value,
                )
                if (reveal.value < 1f) {
                    drawCircle(
                        color = Color.White,
                        radius = radius,
                        center = center,
                        alpha = 0.3f * alpha.value * (1f - reveal.value),
                        style = Stroke(width = 10f),
                    )
                }
            }
            val label = when (phase) {
                AppHandoff.Phase.Waiting -> "Opening ${AppHandoff.launcher?.companionName.orEmpty()}…"
                AppHandoff.Phase.Failed -> "Couldn't open ${AppHandoff.launcher?.companionName.orEmpty()}. Is it installed?"
                else -> null
            }
            if (label != null) {
                Text(
                    text = label,
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White.copy(alpha = alpha.value),
                )
            }
        }
    }
}
