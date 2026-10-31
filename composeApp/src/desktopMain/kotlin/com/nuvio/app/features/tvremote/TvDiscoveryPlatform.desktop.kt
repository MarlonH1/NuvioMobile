package com.nuvio.app.features.tvremote

import java.net.Inet4Address
import java.net.InetAddress
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceListener

// mDNS browsing with JmDNS. DNS-SD types need the ".local." suffix there.
internal actual object TvDiscoveryPlatform {
    private const val SERVICE_TYPE = "$TV_REMOTE_SERVICE_TYPE.local."
    private var jmdns: JmDNS? = null
    private var listener: ServiceListener? = null

    actual val isSupported: Boolean = true

    actual fun start(
        onFound: (serviceName: String, tv: DiscoveredTv) -> Unit,
        onLost: (serviceName: String) -> Unit,
    ) {
        stop()
        val instance = runCatching { JmDNS.create(lanAddress()) }
            .onFailure { TvRemoteLog.w("discovery unavailable: ${it.message}") }
            .getOrNull() ?: return
        val serviceListener = object : ServiceListener {
            override fun serviceAdded(event: ServiceEvent) {
                instance.requestServiceInfo(event.type, event.name, true)
            }

            override fun serviceRemoved(event: ServiceEvent) {
                onLost(event.name)
            }

            override fun serviceResolved(event: ServiceEvent) {
                val info = event.info ?: return
                val host = info.inet4Addresses.firstOrNull()?.hostAddress
                    ?: info.hostAddresses.firstOrNull()
                    ?: return
                val txt = info.propertyNames.toList().associateWith { info.getPropertyString(it).orEmpty() }
                // This computer announces itself too (phone remote host): not a target for itself.
                if (txt["id"] == com.nuvio.app.features.remotehost.DesktopRemoteHost.hostId) return
                val tv = discoveredTvFromTxt(event.name, host, info.port, txt) ?: return
                onFound(event.name, tv)
            }
        }
        instance.addServiceListener(SERVICE_TYPE, serviceListener)
        jmdns = instance
        listener = serviceListener
        TvRemoteLog.i("discovery started ($SERVICE_TYPE)")
    }

    actual fun stop() {
        val instance = jmdns ?: return
        listener?.let { runCatching { instance.removeServiceListener(SERVICE_TYPE, it) } }
        runCatching { instance.close() }
        jmdns = null
        listener = null
    }

    // JmDNS binds to one interface; prefer a real LAN IPv4 address over loopback or VPNs.
    private fun lanAddress(): InetAddress? =
        runCatching {
            java.net.NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback && !it.isVirtual && !it.isPointToPoint }
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
        }.getOrNull()
}
