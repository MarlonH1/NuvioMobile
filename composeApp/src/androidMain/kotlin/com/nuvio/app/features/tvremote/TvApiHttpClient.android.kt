package com.nuvio.app.features.tvremote

import android.annotation.SuppressLint
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.WebSockets
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

// The TV's certificate is self-signed; this client only talks to the paired TV on the LAN.
@SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager")
private object TvSelfSignedTrust : X509TrustManager {
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

internal actual fun createTvApiHttpClient(): HttpClient = HttpClient(OkHttp) {
    expectSuccess = false
    install(WebSockets)
    install(HttpTimeout) {
        connectTimeoutMillis = 5_000
        requestTimeoutMillis = 15_000
    }
    engine {
        config {
            val context = SSLContext.getInstance("TLS").apply { init(null, arrayOf(TvSelfSignedTrust), SecureRandom()) }
            sslSocketFactory(context.socketFactory, TvSelfSignedTrust)
            hostnameVerifier { _, _ -> true }
        }
    }
}
