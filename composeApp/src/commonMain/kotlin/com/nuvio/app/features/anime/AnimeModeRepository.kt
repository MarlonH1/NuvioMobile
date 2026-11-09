package com.nuvio.app.features.anime

import com.nuvio.app.core.storage.ProfileScopedKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Anime4K upscaling preset applied by mpv-based players. */
enum class AnimeUpscalerMode(val storageKey: String, val label: String, val shaderFile: String?) {
    OFF("off", "Off", null),
    FAST("fast", "Fast", "anime4k_fast.glsl"),
    LINE_RECOVERY("line_recovery", "Soft & Clear", "anime4k_line_recovery.glsl"),
    SHARP_DETAIL("sharp_detail", "Sharp & Detailed", "anime4k_sharp_hq.glsl");

    companion object {
        fun fromStorageKey(key: String?): AnimeUpscalerMode =
            entries.firstOrNull { it.storageKey.equals(key, ignoreCase = true) } ?: OFF
    }
}

/** Which titles an enhancement applies to (ported from NuvioCodeineXO's Enhancements menu). */
enum class AnimeEnhancementScope(val storageKey: String, val label: String) {
    OFF("off", "Off"),
    ANIME_ONLY("anime", "Anime Only"),
    ALL_MEDIA("all", "All Media");

    fun appliesTo(isAnime: Boolean): Boolean = when (this) {
        OFF -> false
        ANIME_ONLY -> isAnime
        ALL_MEDIA -> true
    }

    companion object {
        fun fromStorageKey(key: String?, default: AnimeEnhancementScope): AnimeEnhancementScope =
            entries.firstOrNull { it.storageKey.equals(key, ignoreCase = true) } ?: default
    }
}

data class AnimeModeState(
    val fillerBadges: Boolean = true,
    val hideFillers: Boolean = false,
    val upscaler: AnimeUpscalerMode = AnimeUpscalerMode.OFF,
    val upscalerScope: AnimeEnhancementScope = AnimeEnhancementScope.ANIME_ONLY,
    val smoothMotion: AnimeEnhancementScope = AnimeEnhancementScope.OFF,
) {
    val showFillerBadges: Boolean get() = fillerBadges
    val hidingFillers: Boolean get() = fillerBadges && hideFillers

    /** Shader preset for a title, or OFF. */
    fun upscalerFor(isAnime: Boolean): AnimeUpscalerMode =
        if (upscalerScope.appliesTo(isAnime)) upscaler else AnimeUpscalerMode.OFF

    fun smoothMotionFor(isAnime: Boolean): Boolean = smoothMotion.appliesTo(isAnime)
}

/**
 * Anime enhancements ported from NuvioCodeineXO: filler markers, hiding filler episodes,
 * Anime4K upscaling and smooth motion. Each is a regular setting; none changes the app's look.
 */
object AnimeModeRepository {
    private const val FILLER_BADGES_KEY = "anime_mode_filler_badges"
    private const val HIDE_FILLERS_KEY = "anime_mode_hide_fillers"
    private const val UPSCALER_KEY = "anime_mode_upscaler"
    private const val UPSCALER_SCOPE_KEY = "anime_mode_upscaler_scope"
    private const val SMOOTH_MOTION_KEY = "anime_mode_smooth_motion"

    private val _state = MutableStateFlow(AnimeModeState())
    val state: StateFlow<AnimeModeState> = _state.asStateFlow()

    private var loadedScope: String? = null

    fun ensureLoaded() {
        val scope = ProfileScopedKey.of("")
        if (loadedScope == scope) return
        loadedScope = scope
        _state.value = AnimeModeState(
            fillerBadges = AnimeModeStorage.loadBoolean(ProfileScopedKey.of(FILLER_BADGES_KEY)) ?: true,
            hideFillers = AnimeModeStorage.loadBoolean(ProfileScopedKey.of(HIDE_FILLERS_KEY)) ?: false,
            upscaler = AnimeUpscalerMode.fromStorageKey(
                AnimeModeStorage.loadString(ProfileScopedKey.of(UPSCALER_KEY)),
            ),
            upscalerScope = AnimeEnhancementScope.fromStorageKey(
                AnimeModeStorage.loadString(ProfileScopedKey.of(UPSCALER_SCOPE_KEY)),
                AnimeEnhancementScope.ANIME_ONLY,
            ),
            smoothMotion = AnimeEnhancementScope.fromStorageKey(
                AnimeModeStorage.loadString(ProfileScopedKey.of(SMOOTH_MOTION_KEY)),
                AnimeEnhancementScope.OFF,
            ),
        )
    }

    fun snapshot(): AnimeModeState {
        ensureLoaded()
        return _state.value
    }

    fun setFillerBadges(enabled: Boolean) = update { it.copy(fillerBadges = enabled) }
    fun setHideFillers(hide: Boolean) = update { it.copy(hideFillers = hide) }
    fun setUpscaler(mode: AnimeUpscalerMode) = update { it.copy(upscaler = mode) }
    fun setUpscalerScope(scope: AnimeEnhancementScope) = update { it.copy(upscalerScope = scope) }
    fun setSmoothMotion(scope: AnimeEnhancementScope) = update { it.copy(smoothMotion = scope) }

    private inline fun update(transform: (AnimeModeState) -> AnimeModeState) {
        ensureLoaded()
        val previous = _state.value
        val next = transform(previous)
        if (next == previous) return
        _state.value = next
        if (next.fillerBadges != previous.fillerBadges) {
            AnimeModeStorage.saveBoolean(ProfileScopedKey.of(FILLER_BADGES_KEY), next.fillerBadges)
        }
        if (next.hideFillers != previous.hideFillers) {
            AnimeModeStorage.saveBoolean(ProfileScopedKey.of(HIDE_FILLERS_KEY), next.hideFillers)
        }
        if (next.upscaler != previous.upscaler) {
            AnimeModeStorage.saveString(ProfileScopedKey.of(UPSCALER_KEY), next.upscaler.storageKey)
        }
        if (next.upscalerScope != previous.upscalerScope) {
            AnimeModeStorage.saveString(ProfileScopedKey.of(UPSCALER_SCOPE_KEY), next.upscalerScope.storageKey)
        }
        if (next.smoothMotion != previous.smoothMotion) {
            AnimeModeStorage.saveString(ProfileScopedKey.of(SMOOTH_MOTION_KEY), next.smoothMotion.storageKey)
        }
    }
}
