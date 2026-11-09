package com.nuvio.app.features.anime

import com.nuvio.app.core.storage.DesktopCache
import com.nuvio.app.core.storage.DesktopStorage

internal actual object AnimeModeStorage {
    private val store = DesktopStorage.store("nuvio_anime_mode")

    actual fun loadBoolean(key: String): Boolean? = store.getBoolean(key)

    actual fun saveBoolean(key: String, value: Boolean) {
        store.putBoolean(key, value)
    }

    actual fun loadString(key: String): String? = store.getString(key)

    actual fun saveString(key: String, value: String) {
        store.putString(key, value)
    }

    actual fun writeShaderFile(fileName: String, bytes: ByteArray): String? =
        DesktopCache.installVersionedFiles("anime4k", mapOf(fileName to bytes))
            .resolve(fileName)
            .toAbsolutePath()
            .toString()
            .replace('\\', '/')
}
