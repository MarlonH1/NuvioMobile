package com.nuvio.app.features.tvremote

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.WebSockets
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

// The TV's certificate is self-signed; this client only talks to the paired TV on the LAN.
private object TvSelfSignedTrust : X509TrustManager {
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

internal actual fun createTvApiHttpClient(): HttpClient = HttpClient(CIO) {
    expectSuccess = false
    install(WebSockets)
    install(HttpTimeout) {
        connectTimeoutMillis = 5_000
        requestTimeoutMillis = 15_000
    }
    engine {
        https {
            trustManager = TvSelfSignedTrust
        }
    }
}
