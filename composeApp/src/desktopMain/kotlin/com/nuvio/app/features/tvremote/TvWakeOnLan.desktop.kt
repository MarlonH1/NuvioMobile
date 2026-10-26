package com.nuvio.app.features.tvremote

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

internal actual fun sendWakeOnLan(packet: ByteArray, host: String) {
    DatagramSocket().use { socket ->
        socket.broadcast = true
        for (target in listOf("255.255.255.255", host)) {
            runCatching { socket.send(DatagramPacket(packet, packet.size, InetAddress.getByName(target), 9)) }
                .onFailure { TvRemoteLog.w("wol to $target failed", it) }
        }
    }
}
