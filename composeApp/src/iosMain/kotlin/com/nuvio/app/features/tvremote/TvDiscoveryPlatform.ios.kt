package com.nuvio.app.features.tvremote

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.pointed
import kotlinx.cinterop.reinterpret
import platform.Foundation.NSData
import platform.Foundation.NSNetService
import platform.Foundation.NSNetServiceBrowser
import platform.Foundation.NSNetServiceBrowserDelegateProtocol
import platform.Foundation.NSNetServiceDelegateProtocol
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.darwin.NSObject
import platform.posix.AF_INET
import platform.posix.sockaddr
import platform.posix.sockaddr_in

// Bonjour browsing. Needs NSBonjourServices = _nuvio-remote._tcp in Info.plist (iOS 14+ local
// network privacy); the system asks the user for local network access on first use.
internal actual object TvDiscoveryPlatform {
    private var browser: NSNetServiceBrowser? = null
    private var delegate: BrowserDelegate? = null

    actual val isSupported: Boolean = true

    actual fun start(
        onFound: (serviceName: String, tv: DiscoveredTv) -> Unit,
        onLost: (serviceName: String) -> Unit,
    ) {
        stop()
        val browserDelegate = BrowserDelegate(onFound, onLost)
        delegate = browserDelegate
        browser = NSNetServiceBrowser().apply {
            setDelegate(browserDelegate)
            searchForServicesOfType("$TV_REMOTE_SERVICE_TYPE.", inDomain = "local.")
        }
    }

    actual fun stop() {
        browser?.stop()
        browser = null
        delegate?.release()
        delegate = null
    }
}

private class BrowserDelegate(
    private val onFound: (String, DiscoveredTv) -> Unit,
    private val onLost: (String) -> Unit,
) : NSObject(), NSNetServiceBrowserDelegateProtocol, NSNetServiceDelegateProtocol {
    // NSNetService must stay referenced while it resolves.
    private val services = mutableMapOf<String, NSNetService>()

    @ObjCSignatureOverride
    override fun netServiceBrowser(browser: NSNetServiceBrowser, didFindService: NSNetService, moreComing: Boolean) {
        services[didFindService.name] = didFindService
        didFindService.setDelegate(this)
        didFindService.resolveWithTimeout(5.0)
    }

    @ObjCSignatureOverride
    override fun netServiceBrowser(browser: NSNetServiceBrowser, didRemoveService: NSNetService, moreComing: Boolean) {
        services.remove(didRemoveService.name)?.stop()
        onLost(didRemoveService.name)
    }

    override fun netServiceDidResolveAddress(sender: NSNetService) {
        val host = ipv4Address(sender) ?: sender.hostName?.removeSuffix(".")
        val tv = discoveredTvFromTxt(sender.name, host.orEmpty(), sender.port.toInt(), txtRecord(sender))
        if (tv != null) onFound(sender.name, tv)
    }

    fun release() {
        services.values.forEach { it.stop() }
        services.clear()
    }
}

private fun txtRecord(service: NSNetService): Map<String, String> {
    val data = service.TXTRecordData() ?: return emptyMap()
    return NSNetService.dictionaryFromTXTRecordData(data).entries.mapNotNull { (key, value) ->
        val name = key as? String ?: return@mapNotNull null
        val text = (value as? NSData)?.let { NSString.create(data = it, encoding = NSUTF8StringEncoding)?.toString() }
        name to text.orEmpty()
    }.toMap()
}

@OptIn(ExperimentalForeignApi::class)
private fun ipv4Address(service: NSNetService): String? {
    service.addresses?.forEach { entry ->
        val data = entry as? NSData ?: return@forEach
        val bytes = data.bytes ?: return@forEach
        if (bytes.reinterpret<sockaddr>().pointed.sa_family.toInt() != AF_INET) return@forEach
        // s_addr is in network byte order, so its first byte in memory is the first octet.
        val raw = bytes.reinterpret<sockaddr_in>().pointed.sin_addr.s_addr
        return listOf(0, 8, 16, 24).joinToString(".") { shift -> ((raw shr shift) and 0xFFu).toString() }
    }
    return null
}
