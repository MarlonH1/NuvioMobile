package com.nuvio.app.features.tvremote

import android.content.Context
import android.content.SharedPreferences

internal actual object TvRemoteStorage {
    private const val preferencesName = "nuvio_tv_remote"
    private const val enabledKey = "enabled"
    private const val pairedTvsKey = "paired_tvs"
    private const val activeTvKey = "active_tv"
    private const val clientIdKey = "client_id"

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        TvRemoteAndroid.initialize(context)
    }

    actual fun loadEnabled(): Boolean? =
        preferences?.let { prefs ->
            if (prefs.contains(enabledKey)) prefs.getBoolean(enabledKey, false) else null
        }

    actual fun saveEnabled(enabled: Boolean) {
        preferences?.edit()?.putBoolean(enabledKey, enabled)?.apply()
    }

    actual fun loadPairedTvsJson(): String? = preferences?.getString(pairedTvsKey, null)

    actual fun savePairedTvsJson(json: String) {
        preferences?.edit()?.putString(pairedTvsKey, json)?.apply()
    }

    actual fun loadActiveTvId(): String? = preferences?.getString(activeTvKey, null)

    actual fun saveActiveTvId(id: String?) {
        preferences?.edit()?.apply {
            if (id == null) remove(activeTvKey) else putString(activeTvKey, id)
        }?.apply()
    }

    actual fun loadClientId(): String? = preferences?.getString(clientIdKey, null)

    actual fun saveClientId(id: String) {
        preferences?.edit()?.putString(clientIdKey, id)?.apply()
    }
}
