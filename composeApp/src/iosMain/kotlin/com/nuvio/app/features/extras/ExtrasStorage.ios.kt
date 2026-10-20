package com.nuvio.app.features.extras

import com.nuvio.app.core.storage.ProfileScopedKey
import platform.Foundation.NSUserDefaults

internal actual object ExtrasStorage {
    private const val prefix = "nuvio_extras_"

    actual fun loadProfileValue(key: String): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(ProfileScopedKey.of(prefix + key))

    actual fun saveProfileValue(key: String, value: String) {
        NSUserDefaults.standardUserDefaults.setObject(value, forKey = ProfileScopedKey.of(prefix + key))
    }

    actual fun loadDeviceValue(key: String): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(prefix + key)

    actual fun saveDeviceValue(key: String, value: String) {
        NSUserDefaults.standardUserDefaults.setObject(value, forKey = prefix + key)
    }
}
