package com.nuvio.app.features.tvremote

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSURLAuthenticationMethodServerTrust
import platform.Foundation.NSURLCredential
import platform.Foundation.NSURLSessionAuthChallengePerformDefaultHandling
import platform.Foundation.NSURLSessionAuthChallengeUseCredential
import platform.Foundation.credentialForTrust
import platform.Foundation.serverTrust

// The TV's certificate is self-signed; this client only talks to the paired TV on the LAN.
@OptIn(ExperimentalForeignApi::class)
internal actual fun createTvApiHttpClient(): HttpClient = HttpClient(Darwin) {
    expectSuccess = false
    install(WebSockets)
    install(HttpTimeout) {
        connectTimeoutMillis = 5_000
        requestTimeoutMillis = 15_000
    }
    engine {
        handleChallenge { _, _, challenge, completionHandler ->
            val trust = challenge.protectionSpace.serverTrust
            if (challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust && trust != null) {
                completionHandler(NSURLSessionAuthChallengeUseCredential, NSURLCredential.credentialForTrust(trust))
            } else {
                completionHandler(NSURLSessionAuthChallengePerformDefaultHandling, null)
            }
        }
    }
}
