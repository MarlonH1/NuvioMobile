package com.nuvio.app.features.extras

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExtrasLogicTest {
    @Test
    fun animeDetectionMatchesTheTvRules() {
        assertTrue(isAnimeContent("kitsu:1", "series", emptyList(), null, null))
        assertTrue(isAnimeContent("tt1", "anime", emptyList(), null, null))
        assertTrue(isAnimeContent("tt1", "series", listOf("Anime"), null, null))
        assertTrue(isAnimeContent("tt1", "series", listOf("Animation"), "Japan", null))
        assertTrue(isAnimeContent("tt1", "movie", listOf("Animation"), null, "ja"))
        assertFalse(isAnimeContent("tt1", "movie", listOf("Animation"), "United States", "en"))
        assertFalse(isAnimeContent("tt1", "series", listOf("Drama"), "Japan", "ja"))
    }

    @Test
    fun ratingsUseHalfStepsBetweenZeroAndTen() {
        assertEquals(7.5, normalizePersonalRating(7.3))
        assertEquals(7.0, normalizePersonalRating(7.2))
        assertEquals(0.0, normalizePersonalRating(-2.0))
        assertEquals(10.0, normalizePersonalRating(12.0))
        assertEquals("8.0", formatPersonalRating(8.0))
        assertEquals("9.5", formatPersonalRating(9.5))
    }

    @Test
    fun ratingKeysMatchTheTvApp() {
        assertEquals("title:tt0111161", personalRatingKey("tt0111161"))
        assertEquals("episode:tt1:1:2", personalRatingKey("tt1", "tt1:1:2", episode = true))
        assertNull(personalRatingKey(""))
    }

    @Test
    fun defaultLayoutKeepsFiveInTheBarAndRateInMore() {
        val layout = normalizedControlLayout(emptyList())
        assertEquals(5, layout.count { it.placement == PlayerControlPlacement.Bar })
        assertEquals(PlayerControlPlacement.More, layout.first { it.id == PlayerControlId.Rate }.placement)
    }

    @Test
    fun savedLayoutsGainNewControlsAndMoveWorks() {
        val saved = listOf(PlayerControlSlot("Speed", "Bar"), PlayerControlSlot("Bogus", "Bar"))
        val layout = normalizedControlLayout(saved)
        assertEquals(PlayerControlId.Speed, layout.first().id)
        assertEquals(PlayerControlId.entries.size, layout.size)
        val moved = moveControl(layout, PlayerControlId.Rate, PlayerControlPlacement.Bar, 1)
        assertEquals(PlayerControlId.Rate, moved.first().id)
        assertEquals(PlayerControlPlacement.Bar, moved.first().placement)
    }

    @Test
    fun friendCodesNormalizeAndFormat() {
        assertEquals("ABCD2345EFGH", normalizeFriendCode("abcd-2345-efgh"))
        assertTrue(isValidFriendCode("ABCD2345EFGH"))
        assertFalse(isValidFriendCode("ABCD2345EFG1"))
        assertEquals("ABCD-2345-EFGH", formatFriendCode("ABCD2345EFGH"))
    }

    @Test
    fun remoteButtonsKeepOrderAndAddNewOnesEnabled() {
        val defaults = normalizedRemoteButtons(emptyList())
        assertEquals(RemoteButtonId.entries.toList(), defaults.map { it.id })
        assertTrue(defaults.all { it.enabled })
        val saved = listOf(RemoteButtonSlot("Exit", enabled = false), RemoteButtonSlot("Remote"), RemoteButtonSlot("Bogus"))
        val buttons = normalizedRemoteButtons(saved)
        assertEquals(listOf(RemoteButtonId.Exit, RemoteButtonId.Remote), buttons.take(2).map { it.id })
        assertFalse(buttons.first().enabled)
        assertEquals(RemoteButtonId.entries.size, buttons.size)
    }
}
