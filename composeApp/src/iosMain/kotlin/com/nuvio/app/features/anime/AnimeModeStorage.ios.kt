package com.nuvio.app.features.anime

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSUserDefaults
import platform.Foundation.dataWithBytes
import platform.Foundation.writeToFile

internal actual object AnimeModeStorage {
    private val defaults get() = NSUserDefaults.standardUserDefaults

    actual fun loadBoolean(key: String): Boolean? =
        if (defaults.objectForKey(key) != null) defaults.boolForKey(key) else null

    actual fun saveBoolean(key: String, value: Boolean) {
        defaults.setBool(value, forKey = key)
    }

    actual fun loadString(key: String): String? = defaults.stringForKey(key)

    actual fun saveString(key: String, value: String) {
        defaults.setObject(value, forKey = key)
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun writeShaderFile(fileName: String, bytes: ByteArray): String? {
        val caches = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true)
            .firstOrNull() as? String ?: return null
        val dir = "$caches/anime4k"
        NSFileManager.defaultManager.createDirectoryAtPath(dir, true, null, null)
        val path = "$dir/$fileName"
        val data = bytes.usePinned { NSData.dataWithBytes(it.addressOf(0), bytes.size.toULong()) }
        return if (data.writeToFile(path, atomically = true)) path else null
    }
}
