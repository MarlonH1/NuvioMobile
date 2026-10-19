package com.nuvio.app.features.tvremote

import platform.Foundation.NSUserDefaults

internal actual object TvRemoteStorage {
    private const val enabledKey = "tv_remote_enabled"
    private const val pairedTvsKey = "tv_remote_paired_tvs"
    private const val activeTvKey = "tv_remote_active_tv"
    private const val clientIdKey = "tv_remote_client_id"

    private val defaults: NSUserDefaults
        get() = NSUserDefaults.standardUserDefaults

    actual fun loadEnabled(): Boolean? =
        if (defaults.objectForKey(enabledKey) != null) defaults.boolForKey(enabledKey) else null

    actual fun saveEnabled(enabled: Boolean) {
        defaults.setBool(enabled, forKey = enabledKey)
    }

    actual fun loadPairedTvsJson(): String? = defaults.stringForKey(pairedTvsKey)

    actual fun savePairedTvsJson(json: String) {
        defaults.setObject(json, forKey = pairedTvsKey)
    }

    actual fun loadActiveTvId(): String? = defaults.stringForKey(activeTvKey)

    actual fun saveActiveTvId(id: String?) {
        if (id == null) defaults.removeObjectForKey(activeTvKey) else defaults.setObject(id, forKey = activeTvKey)
    }

    actual fun loadClientId(): String? = defaults.stringForKey(clientIdKey)

    actual fun saveClientId(id: String) {
        defaults.setObject(id, forKey = clientIdKey)
    }
}
