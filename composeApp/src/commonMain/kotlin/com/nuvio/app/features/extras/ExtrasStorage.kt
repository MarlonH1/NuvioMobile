package com.nuvio.app.features.extras

/**
 * JSON payloads for the fork's extra features. Profile values follow the active
 * profile (settings, ratings); device values are shared (friend code and list).
 */
internal expect object ExtrasStorage {
    fun loadProfileValue(key: String): String?
    fun saveProfileValue(key: String, value: String)
    fun loadDeviceValue(key: String): String?
    fun saveDeviceValue(key: String, value: String)
}
