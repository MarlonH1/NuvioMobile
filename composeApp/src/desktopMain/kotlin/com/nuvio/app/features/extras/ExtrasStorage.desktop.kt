package com.nuvio.app.features.extras

import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.core.storage.ProfileScopedKey

internal actual object ExtrasStorage {
    private val store = DesktopStorage.store("nuvio_extras")

    actual fun loadProfileValue(key: String): String? = store.getString(ProfileScopedKey.of(key))

    actual fun saveProfileValue(key: String, value: String) {
        store.putString(ProfileScopedKey.of(key), value)
    }

    actual fun loadDeviceValue(key: String): String? = store.getString(key)

    actual fun saveDeviceValue(key: String, value: String) {
        store.putString(key, value)
    }
}
