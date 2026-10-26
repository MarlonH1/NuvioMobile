package com.nuvio.app.core.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal expect val nuvioPlatformExtraTopPadding: Dp
internal expect val nuvioPlatformExtraBottomPadding: Dp
internal expect val nuvioBottomNavigationExtraVerticalPadding: Dp
@Composable
internal expect fun nuvioBottomNavigationBarInsets(): WindowInsets

/** Physical display-safe top inset, excluding any enclosing native toolbar. */
@Composable
internal expect fun platformPhysicalTopInset(): Dp

@Composable
internal expect fun ResizeForKeyboard()

internal val LocalNuvioBottomNavigationOverlayPadding = staticCompositionLocalOf { 0.dp }

internal val LocalBottomInsetsConsumed = staticCompositionLocalOf { false }

/**
 * How far the Compose bottom tab bar reaches up from the bottom of the screen, as measured (0 when
 * none is shown). Overlays outside the tab host, like the TV now-playing bar, stay above it.
 */
internal object NuvioBottomTabBarSpace {
    var height by mutableStateOf(0.dp)
}

/** Space floating overlays above the bottom bars take (the TV now-playing bar), 0 when hidden. */
internal object NuvioBottomOverlaySpace {
    var height by mutableStateOf(0.dp)
}

/** Put on a bottom-anchored tab bar so [NuvioBottomTabBarSpace] follows its size. */
@Composable
internal fun Modifier.reportsBottomTabBarHeight(): Modifier {
    val density = LocalDensity.current
    DisposableEffect(Unit) { onDispose { NuvioBottomTabBarSpace.height = 0.dp } }
    return onSizeChanged { NuvioBottomTabBarSpace.height = with(density) { it.height.toDp() } }
}

/** CompositionLocal providing the shared [NuvioNavBarScrollState] so child screens can attach the nestedScrollConnection. */
val LocalNuvioNavBarScrollState = staticCompositionLocalOf<NuvioNavBarScrollState?> { null }

@Composable
internal fun nuvioSafeBottomPadding(extra: Dp = 0.dp): Dp {
	if (LocalBottomInsetsConsumed.current) return extra
	val navigationBarBottom = nuvioBottomNavigationBarInsets()
		.asPaddingValues()
		.calculateBottomPadding()
	return navigationBarBottom.coerceAtLeast(nuvioPlatformExtraBottomPadding) +
		LocalNuvioBottomNavigationOverlayPadding.current +
		extra
}

/**
 * Bottom padding for scrolling screen content: lets the last item scroll up above the tab bar
 * (measured, whatever its style) and the now-playing bar, so neither covers it at the end.
 * Sheets and dialogs keep using [nuvioSafeBottomPadding].
 */
@Composable
internal fun nuvioContentBottomPadding(extra: Dp = 0.dp): Dp {
	val safe = nuvioSafeBottomPadding()
	// Floating tab bars draw over the content; their real height can exceed the fixed overlay.
	val bars = if (LocalNuvioBottomNavigationOverlayPadding.current > 0.dp) {
		maxOf(safe, NuvioBottomTabBarSpace.height)
	} else {
		safe
	}
	return bars + NuvioBottomOverlaySpace.height + extra
}
