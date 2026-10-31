package com.nuvio.app.features.updater

import com.nuvio.app.core.build.AppVersionConfig

/**
 * Releases of this fork are tagged `shad-<yyyyMMdd>-<HHmm>-<commit>` with the UTC commit time,
 * the same stamp the build carries in [AppVersionConfig.FORK_BUILD]. A release is newer when its
 * commit is newer, regardless of the version name (which stays the upstream one).
 */
internal object ForkBuilds {
    private val tagPattern = Regex("""^shad-(\d{8})-(\d{4})-[0-9a-f]+$""")

    val enabled: Boolean
        get() = AppVersionConfig.FORK_BUILD.length == 12 && AppVersionConfig.FORK_BUILD.all(Char::isDigit)

    /** yyyyMMddHHmm of a fork release tag, or null for other tags. */
    fun stamp(tag: String?): Long? =
        tagPattern.matchEntire(tag.orEmpty().trim())?.let { "${it.groupValues[1]}${it.groupValues[2]}".toLongOrNull() }

    /** Null when [tag] is not a fork release (then the normal version comparison applies). */
    fun isRemoteNewer(tag: String, localBuild: String = AppVersionConfig.FORK_BUILD): Boolean? {
        val remote = stamp(tag) ?: return null
        val local = localBuild.toLongOrNull() ?: return true
        return remote > local
    }
}
