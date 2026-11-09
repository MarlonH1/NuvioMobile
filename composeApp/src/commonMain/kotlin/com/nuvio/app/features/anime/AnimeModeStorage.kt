package com.nuvio.app.features.anime

/** Small key/value store backing anime settings and the filler cache. */
internal expect object AnimeModeStorage {
    fun loadBoolean(key: String): Boolean?
    fun saveBoolean(key: String, value: Boolean)
    fun loadString(key: String): String?
    fun saveString(key: String, value: String)

    /** Writes an Anime4K shader to a private cache file and returns its absolute path. */
    fun writeShaderFile(fileName: String, bytes: ByteArray): String?
}
