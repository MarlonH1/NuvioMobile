package com.nuvio.app.features.extras

import android.content.Context
import android.content.SharedPreferences
import com.nuvio.app.core.storage.ProfileScopedKey

internal actual object ExtrasStorage {
    private const val preferencesName = "nuvio_extras"

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    }

    actual fun loadProfileValue(key: String): String? =
        preferences?.getString(ProfileScopedKey.of(key), null)

    actual fun saveProfileValue(key: String, value: String) {
        preferences?.edit()?.putString(ProfileScopedKey.of(key), value)?.apply()
    }

    actual fun loadDeviceValue(key: String): String? = preferences?.getString(key, null)

    actual fun saveDeviceValue(key: String, value: String) {
        preferences?.edit()?.putString(key, value)?.apply()
    }
}
