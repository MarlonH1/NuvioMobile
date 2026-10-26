package com.nuvio.app.features.tvremote

/** Sends [packet] to UDP port 9, as LAN broadcast and directly to [host]. Blocking. */
internal expect fun sendWakeOnLan(packet: ByteArray, host: String)

/** Magic packet: 6 × 0xFF, then the MAC 16 times. `null` for a malformed MAC. */
internal fun wakeOnLanPacket(mac: String): ByteArray? {
    val bytes = mac.trim().split(':', '-')
        .takeIf { it.size == 6 }
        ?.map { part -> part.takeIf { it.length == 2 }?.toIntOrNull(16)?.toByte() ?: return null }
        ?: return null
    return ByteArray(6) { 0xFF.toByte() } + List(16) { bytes }.flatten().toByteArray()
}
