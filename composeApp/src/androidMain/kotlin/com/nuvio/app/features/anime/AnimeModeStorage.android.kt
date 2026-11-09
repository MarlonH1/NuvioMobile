package com.nuvio.app.features.anime

import android.content.Context
import android.content.SharedPreferences
import java.io.File

internal actual object AnimeModeStorage {
    private const val PREFERENCES_NAME = "nuvio_anime_mode"
    private var preferences: SharedPreferences? = null
    private var shaderDir: File? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        shaderDir = File(context.cacheDir, "anime4k")
    }

    actual fun loadBoolean(key: String): Boolean? =
        preferences?.takeIf { it.contains(key) }?.getBoolean(key, false)

    actual fun saveBoolean(key: String, value: Boolean) {
        preferences?.edit()?.putBoolean(key, value)?.apply()
    }

    actual fun loadString(key: String): String? = preferences?.getString(key, null)

    actual fun saveString(key: String, value: String) {
        preferences?.edit()?.putString(key, value)?.apply()
    }

    actual fun writeShaderFile(fileName: String, bytes: ByteArray): String? {
        val dir = shaderDir ?: return null
        dir.mkdirs()
        val file = File(dir, fileName)
        if (!file.exists() || file.length() != bytes.size.toLong()) file.writeBytes(bytes)
        return file.absolutePath
    }
}
