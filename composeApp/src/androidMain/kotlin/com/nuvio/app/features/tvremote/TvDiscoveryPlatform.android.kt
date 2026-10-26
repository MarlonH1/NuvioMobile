package com.nuvio.app.features.tvremote

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import java.net.Inet4Address
import java.util.concurrent.Executors

internal actual object TvDiscoveryPlatform {
    private val executor = Executors.newSingleThreadExecutor()
    private var manager: NsdManager? = null
    private var listener: NsdManager.DiscoveryListener? = null
    private val infoCallbacks = mutableMapOf<String, NsdManager.ServiceInfoCallback>()

    actual val isSupported: Boolean get() = TvRemoteAndroid.appContext != null

    actual fun start(
        onFound: (serviceName: String, tv: DiscoveredTv) -> Unit,
        onLost: (serviceName: String) -> Unit,
    ) {
        val context = TvRemoteAndroid.appContext ?: return
        stop()
        val nsd = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        manager = nsd
        val discovery = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                TvRemoteLog.i("discovery started ($serviceType)")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                resolve(nsd, service, onFound)
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                synchronized(infoCallbacks) { infoCallbacks.remove(service.serviceName) }
                    ?.let { runCatching { nsd.unregisterServiceInfoCallback(it) } }
                onLost(service.serviceName)
            }

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                TvRemoteLog.w("discovery failed to start: $errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }
        listener = discovery
        runCatching { nsd.discoverServices(TV_REMOTE_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discovery) }
            .onFailure { TvRemoteLog.w("discovery unavailable: ${it.message}") }
    }

    actual fun stop() {
        val nsd = manager ?: return
        listener?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        listener = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val callbacks = synchronized(infoCallbacks) { infoCallbacks.values.toList().also { infoCallbacks.clear() } }
            callbacks.forEach { runCatching { nsd.unregisterServiceInfoCallback(it) } }
        }
    }

    private fun resolve(
        nsd: NsdManager,
        service: NsdServiceInfo,
        onFound: (String, DiscoveredTv) -> Unit,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+: a callback that also reports later address changes of the TV.
            val callback = object : NsdManager.ServiceInfoCallback {
                override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                    TvRemoteLog.w("resolve failed for ${service.serviceName}: $errorCode")
                }

                override fun onServiceUpdated(info: NsdServiceInfo) {
                    val host = info.hostAddresses.firstOrNull { it is Inet4Address }
                        ?: info.hostAddresses.firstOrNull()
                    report(info, host?.hostAddress, onFound)
                }

                override fun onServiceLost() = Unit

                override fun onServiceInfoCallbackUnregistered() = Unit
            }
            synchronized(infoCallbacks) {
                if (infoCallbacks.containsKey(service.serviceName)) return
                infoCallbacks[service.serviceName] = callback
            }
            runCatching { nsd.registerServiceInfoCallback(service, executor, callback) }
                .onFailure { TvRemoteLog.w("resolve unavailable: ${it.message}") }
        } else {
            @Suppress("DEPRECATION")
            nsd.resolveService(
                service,
                object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                        TvRemoteLog.w("resolve failed for ${info.serviceName}: $errorCode")
                    }

                    override fun onServiceResolved(info: NsdServiceInfo) {
                        @Suppress("DEPRECATION")
                        report(info, info.host?.hostAddress, onFound)
                    }
                },
            )
        }
    }

    private fun report(info: NsdServiceInfo, host: String?, onFound: (String, DiscoveredTv) -> Unit) {
        val txt = info.attributes.mapValues { (_, value) -> value?.decodeToString().orEmpty() }
        val tv = discoveredTvFromTxt(info.serviceName, host.orEmpty(), info.port, txt) ?: return
        onFound(info.serviceName, tv)
    }
}
