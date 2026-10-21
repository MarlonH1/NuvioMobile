package com.nuvio.app.features.tvremote

import com.nuvio.app.features.tvremote.protocol.RemoteMessage
import com.nuvio.app.features.tvremote.protocol.RemoteMessageType
import com.nuvio.app.features.tvremote.protocol.remoteProtocolJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemotePartyProtocolTest {
    @Test
    fun partyStateFromTheTvDecodes() {
        val message = remoteProtocolJson.decodeFromString<RemoteMessage>(
            """{"v":1,"type":"party.state","party":{"code":"ABC234","title":"Frieren","season":1,"episode":2,
               "poster":null,"maxMembers":3,"isHost":true,"members":[{"name":"Marlon","host":true,"self":true}]}}""",
        )
        assertEquals(RemoteMessageType.PARTY_STATE, message.type)
        val party = message.party!!
        assertEquals("ABC234", party.code)
        assertEquals(3, party.maxMembers)
        assertTrue(party.members.single().self)
    }

    @Test
    fun leavingThePartyArrivesAsNull() {
        val message = remoteProtocolJson.decodeFromString<RemoteMessage>("""{"v":1,"type":"party.state","party":null}""")
        assertNull(message.party)
    }

    @Test
    fun inviteRequestAndResultUseFlatFields() {
        val request = remoteProtocolJson.encodeToString(
            RemoteMessage.serializer(),
            RemoteMessage(type = RemoteMessageType.PARTY_INVITE, id = "invite-GGGGHHHHJJJJ", friendCode = "GGGGHHHHJJJJ"),
        )
        assertTrue(""""friendCode":"GGGGHHHHJJJJ"""" in request)
        val result = remoteProtocolJson.decodeFromString<RemoteMessage>(
            """{"v":1,"type":"party.invite.result","id":"invite-GGGGHHHHJJJJ","ok":false,"code":"party_full","friendCode":"GGGGHHHHJJJJ"}""",
        )
        assertFalse(result.ok!!)
        assertEquals("party_full", result.code)
    }
}
