package com.nuvio.app.features.player.desktop

import com.nuvio.app.features.player.PlayerPlaybackStats
import com.nuvio.app.features.player.SubtitleStyleState

/**
 * Linux build: the upstream helper calls the Windows mpv DLL through java.lang.foreign, which
 * neither exists on JDK 17 nor does anything off Windows (every entry point returned early).
 * Same API, no-ops.
 */
internal object WindowsMpvSubStyleHelper {
    fun supportsCacheSpeed(): Boolean = false

    fun getCacheSpeed(bridgeHandle: Long): Long = 0L

    fun getPlaybackStats(bridgeHandle: Long): PlayerPlaybackStats = PlayerPlaybackStats()

    fun applyExtendedSubtitleStyle(bridgeHandle: Long, style: SubtitleStyleState, useLibass: Boolean) = Unit
}
