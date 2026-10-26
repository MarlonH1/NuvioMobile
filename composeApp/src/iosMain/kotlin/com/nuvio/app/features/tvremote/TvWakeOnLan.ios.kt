package com.nuvio.app.features.tvremote

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.value
import platform.posix.AF_INET
import platform.posix.INADDR_BROADCAST
import platform.posix.SOCK_DGRAM
import platform.posix.SOL_SOCKET
import platform.posix.SO_BROADCAST
import platform.posix.close
import platform.darwin.inet_addr
import platform.posix.sendto
import platform.posix.setsockopt
import platform.posix.sockaddr_in
import platform.posix.socket

// ponytail: iOS 14+ blocks the broadcast without the multicast entitlement; the unicast to [host] still goes out.
@OptIn(ExperimentalForeignApi::class)
internal actual fun sendWakeOnLan(packet: ByteArray, host: String) {
    val fd = socket(AF_INET, SOCK_DGRAM, 0)
    if (fd < 0) return
    try {
        memScoped {
            val on = alloc<IntVar>().apply { value = 1 }
            setsockopt(fd, SOL_SOCKET, SO_BROADCAST, on.ptr, sizeOf<IntVar>().convert())
            for (address in listOf(INADDR_BROADCAST, inet_addr(host))) {
                val addr = alloc<sockaddr_in>().apply {
                    sin_family = AF_INET.convert()
                    sin_port = (9 shl 8).convert() // port 9, network byte order on little-endian
                    sin_addr.s_addr = address
                }
                packet.usePinned {
                    sendto(fd, it.addressOf(0), packet.size.convert(), 0, addr.ptr.reinterpret(), sizeOf<sockaddr_in>().convert())
                }
            }
        }
    } finally {
        close(fd)
    }
}
