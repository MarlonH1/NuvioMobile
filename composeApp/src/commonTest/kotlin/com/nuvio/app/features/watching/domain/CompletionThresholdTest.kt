package com.nuvio.app.features.watching.domain

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompletionThresholdTest {
    private val hour = 3_600_000L

    @Test
    fun watchedFrom85Percent() {
        assertFalse(isProgressComplete(positionMs = (hour * 0.84).toLong(), durationMs = hour, isEnded = false))
        assertTrue(isProgressComplete(positionMs = (hour * 0.85).toLong(), durationMs = hour, isEnded = false))
    }

    @Test
    fun reachingTheCreditsCountsAsWatched() {
        val credits = (hour * 0.80).toLong()
        assertFalse(isProgressComplete(credits - 1, hour, false, creditsStartMs = credits))
        assertTrue(isProgressComplete(credits, hour, false, creditsStartMs = credits))
    }

    @Test
    fun creditsInTheFirstHalfAreIgnored() {
        val bogus = (hour * 0.30).toLong()
        assertFalse(isProgressComplete((hour * 0.5).toLong(), hour, false, creditsStartMs = bogus))
    }
}
