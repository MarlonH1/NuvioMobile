package com.nuvio.app.features.tvremote.ui

import android.app.Application

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RepeatingPressTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun tapFiresOnceAndHoldKeepsFiring() {
        var count = 0
        compose.setContent {
            Box(Modifier.size(64.dp).testTag("key").repeatingPress { count++ })
        }
        compose.onNodeWithTag("key").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithTag("key").performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(1, count)

        compose.onNodeWithTag("key").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(1_500)
        compose.onNodeWithTag("key").performTouchInput { up() }
        compose.waitForIdle()
        val afterHold = count
        // 1 (tap) + 1 on press + several repeats after the 400 ms start delay
        assertTrue("held press repeated only ${afterHold - 1} times", afterHold - 1 >= 6)
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals("kept firing after release", afterHold, count)
    }
}
