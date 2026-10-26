package com.nuvio.app.features.extras

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PartyFriendsSyncTest {
    private val localCode = "AAAABBBBCCCC"
    private val accountCode = "DDDDEEEEFFFF"
    private val alice = PartyFriend("GGGGHHHHJJJJ", "Alice")
    private val bob = PartyFriend("KKKKLLLLMMMM", "Bob")
    private val fresh = "NNNNPPPPQQQQ"

    private fun merge(local: PartyFriendsState, remote: AccountFriends?) =
        mergeAccountFriends(local, remote, userId = "u1", now = 500, newCode = { fresh })

    @Test
    fun firstSyncWithEmptyAccountUploadsThisDevice() {
        val result = merge(PartyFriendsState(code = localCode, friends = listOf(alice)), null)
        assertTrue(result.push)
        assertEquals(localCode, result.state.code)
        assertEquals(listOf(alice), result.state.friends)
        assertEquals("u1", result.state.accountUserId)
    }

    @Test
    fun firstSyncAdoptsAccountCodeAndKeepsLocalFriends() {
        val result = merge(
            PartyFriendsState(code = localCode, friends = listOf(alice, bob)),
            AccountFriends(code = accountCode, friends = listOf(alice), updated = 100),
        )
        assertEquals(accountCode, result.state.code)
        assertEquals(listOf(alice, bob), result.state.friends)
        assertTrue(result.push)
    }

    @Test
    fun linkedDeviceTakesNewerAccountCopySoRemovalsCarryOver() {
        val result = merge(
            PartyFriendsState(code = accountCode, friends = listOf(alice, bob), accountUserId = "u1", updated = 100),
            AccountFriends(code = accountCode, friends = listOf(alice), updated = 200),
        )
        assertEquals(listOf(alice), result.state.friends)
        assertFalse(result.push)
    }

    @Test
    fun linkedDeviceUploadsNewerLocalChanges() {
        val result = merge(
            PartyFriendsState(code = accountCode, friends = listOf(alice, bob), accountUserId = "u1", updated = 300),
            AccountFriends(code = accountCode, friends = listOf(alice), updated = 200),
        )
        assertEquals(listOf(alice, bob), result.state.friends)
        assertTrue(result.push)
    }

    @Test
    fun switchingAccountsDropsThePreviousAccountsList() {
        val result = merge(
            PartyFriendsState(code = localCode, friends = listOf(alice), accountUserId = "other", updated = 300),
            null,
        )
        assertEquals(fresh, result.state.code)
        assertTrue(result.state.friends.isEmpty())
        assertTrue(result.push)
    }

    @Test
    fun accountListNeverContainsItsOwnCodeOrInvalidCodes() {
        val result = merge(
            PartyFriendsState(),
            AccountFriends(code = accountCode, friends = listOf(PartyFriend(accountCode, "Me"), PartyFriend("bad", "X"), alice), updated = 1),
        )
        assertEquals(listOf(alice), result.state.friends)
    }
}
