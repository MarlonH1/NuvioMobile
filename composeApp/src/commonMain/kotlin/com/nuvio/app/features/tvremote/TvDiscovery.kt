package com.nuvio.app.features.tvremote

/** mDNS / DNS-SD service type the TV daemon announces (see docs/remote-protocol.md). */
internal const val TV_REMOTE_SERVICE_TYPE = "_nuvio-remote._tcp"

/**
 * Platform LAN discovery: Android NsdManager, iOS Bonjour (NSNetServiceBrowser).
 * Callbacks may arrive on any thread.
 *
 * `serviceName` is the DNS-SD instance name. It identifies a TV until it disappears, because
 * a "lost" event only carries the name.
 */
internal expect object TvDiscoveryPlatform {
    val isSupported: Boolean
    fun start(onFound: (serviceName: String, tv: DiscoveredTv) -> Unit, onLost: (serviceName: String) -> Unit)
    fun stop()
}

/** Reads the TXT record the daemon publishes: `v`, `id`, `name`, and `platform` from the desktop app. */
internal fun discoveredTvFromTxt(
    serviceName: String,
    host: String,
    port: Int,
    txt: Map<String, String>,
): DiscoveredTv? {
    if (host.isBlank() || port <= 0) return null
    val id = txt["id"]?.takeIf { it.isNotBlank() } ?: return null
    val name = txt["name"]?.takeIf { it.isNotBlank() }
        ?: serviceName.removePrefix("Nuvio TV ").ifBlank { "Nuvio TV" }
    return DiscoveredTv(id = id, name = name, host = host, port = port, platform = txt["platform"]?.takeIf { it.isNotBlank() })
}
