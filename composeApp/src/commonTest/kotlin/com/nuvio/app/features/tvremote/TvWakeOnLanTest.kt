package com.nuvio.app.features.tvremote

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TvWakeOnLanTest {
    @Test
    fun buildsMagicPacket() {
        val packet = wakeOnLanPacket("a8:23:fe:01:02:0F")!!
        assertEquals(102, packet.size)
        assertEquals(List(6) { 0xFF.toByte() }, packet.take(6))
        assertEquals(listOf(0xA8, 0x23, 0xFE, 0x01, 0x02, 0x0F).map { it.toByte() }, packet.slice(96 until 102))
    }

    @Test
    fun rejectsMalformedMac() {
        assertNull(wakeOnLanPacket("a8:23:fe:01:02"))
        assertNull(wakeOnLanPacket("zz:23:fe:01:02:0f"))
    }
}
