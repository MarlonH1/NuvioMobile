package com.nuvio.app.navigation

import kotlinx.serialization.Serializable

// Routes for the TV remote feature, kept out of Routes.kt to ease upstream merges.

@Serializable
data class TvRemoteDevicesRoute(override val title: String = "") : SettingsDestinationRoute

@Serializable
data class TvRemoteControlRoute(override val title: String = "") : AppRoute {
    override val hidesNavigationBar: Boolean
        get() = true
}
