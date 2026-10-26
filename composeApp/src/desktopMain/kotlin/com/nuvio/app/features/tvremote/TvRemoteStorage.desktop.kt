package com.nuvio.app.features.tvremote

import com.nuvio.app.core.storage.DesktopStorage

internal actual object TvRemoteStorage {
    private const val enabledKey = "enabled"
    private const val pairedTvsKey = "paired_tvs"
    private const val activeTvKey = "active_tv"
    private const val clientIdKey = "client_id"

    private val store = DesktopStorage.store("nuvio_tv_remote")

    actual fun loadEnabled(): Boolean? = store.getString(enabledKey)?.toBooleanStrictOrNull()

    actual fun saveEnabled(enabled: Boolean) {
        store.putString(enabledKey, enabled.toString())
    }

    actual fun loadPairedTvsJson(): String? = store.getString(pairedTvsKey)

    actual fun savePairedTvsJson(json: String) {
        store.putString(pairedTvsKey, json)
    }

    actual fun loadActiveTvId(): String? = store.getString(activeTvKey)

    actual fun saveActiveTvId(id: String?) {
        if (id == null) store.remove(activeTvKey) else store.putString(activeTvKey, id)
    }

    actual fun loadClientId(): String? = store.getString(clientIdKey)

    actual fun saveClientId(id: String) {
        store.putString(clientIdKey, id)
    }
}
