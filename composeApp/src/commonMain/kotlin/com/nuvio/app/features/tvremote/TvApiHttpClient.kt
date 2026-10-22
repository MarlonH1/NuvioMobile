package com.nuvio.app.features.tvremote

import io.ktor.client.HttpClient

/**
 * HTTP/WebSocket client for the TV's own API (wss://<tv>:3001). LG TVs use a self-signed
 * certificate, so this client accepts it; it is only ever used for the paired TV's address.
 */
internal expect fun createTvApiHttpClient(): HttpClient
