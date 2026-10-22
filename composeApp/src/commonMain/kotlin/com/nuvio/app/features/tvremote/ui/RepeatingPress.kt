package com.nuvio.app.features.tvremote.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val REPEAT_START_DELAY_MS = 400L
private const val REPEAT_FIRST_GAP_MS = 160L
private const val REPEAT_MIN_GAP_MS = 50L

/**
 * Like clickable, but fires [onPress] right on touch down and keeps firing while the finger
 * stays down, a little faster with every step (volume keys).
 */
@Composable
internal fun Modifier.repeatingPress(onPress: () -> Unit): Modifier {
    val currentOnPress by rememberUpdatedState(onPress)
    val interaction = remember { MutableInteractionSource() }
    val scope = rememberCoroutineScope()
    return this
        .semantics {
            role = Role.Button
            onClick {
                currentOnPress()
                true
            }
        }
        .indication(interaction, LocalIndication.current)
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = { offset ->
                    val press = PressInteraction.Press(offset)
                    interaction.emit(press)
                    val repeat = scope.launch {
                        currentOnPress()
                        delay(REPEAT_START_DELAY_MS)
                        var gap = REPEAT_FIRST_GAP_MS
                        while (isActive) {
                            currentOnPress()
                            delay(gap)
                            gap = (gap * 0.85f).toLong().coerceAtLeast(REPEAT_MIN_GAP_MS)
                        }
                    }
                    val released = tryAwaitRelease()
                    repeat.cancel()
                    interaction.emit(if (released) PressInteraction.Release(press) else PressInteraction.Cancel(press))
                },
            )
        }
}
