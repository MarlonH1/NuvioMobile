package com.nuvio.app.features.anime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.details.MetaDetails
import kotlin.concurrent.Volatile
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Video enhancements the active mpv player should apply. */
data class AnimePlaybackSettings(
    /** Absolute path of the Anime4K shader to apply, or null for none. */
    val shaderPath: String? = null,
    /** mpv smooth motion (display-resample + interpolation). */
    val smoothMotion: Boolean = false,
)

/**
 * What the active player should do for the anime enhancements. The player screen resolves it before the
 * source loads; mpv-based engines read it when they load a file and listen for live changes.
 */
object AnimePlayback {
    @Volatile
    var settings: AnimePlaybackSettings = AnimePlaybackSettings()
        private set

    val shaderPath: String? get() = settings.shaderPath

    private val _currentIsAnime = MutableStateFlow(false)
    /** Whether the title being played counts as anime (shown in the in-player panel). */
    val currentIsAnime: StateFlow<Boolean> = _currentIsAnime.asStateFlow()

    private val lock = SynchronizedObject()
    private val listeners = mutableListOf<(AnimePlaybackSettings) -> Unit>()

    /** Lets a running player re-apply enhancements when the anime settings change mid-playback. */
    fun addListener(listener: (AnimePlaybackSettings) -> Unit): () -> Unit {
        synchronized(lock) { listeners += listener }
        return { synchronized(lock) { listeners -= listener } }
    }

    internal fun setCurrentIsAnime(isAnime: Boolean) {
        _currentIsAnime.value = isAnime
    }

    internal fun update(next: AnimePlaybackSettings) {
        if (settings == next) return
        settings = next
        synchronized(lock) { listeners.toList() }.forEach { it(next) }
    }
}

/** Keeps [AnimePlayback] in sync with the anime settings for the title being played. */
@Composable
fun AnimePlaybackEffect(
    type: String?,
    id: String?,
    meta: MetaDetails?,
    streamTitle: String? = null,
    sourceFilename: String? = null,
) {
    val mode by remember { AnimeModeRepository.ensureLoaded(); AnimeModeRepository.state }.collectAsStateWithLifecycle()
    val isAnime = remember(type, id, meta, streamTitle, sourceFilename) {
        (if (meta != null) AnimeContent.isAnime(meta) else AnimeContent.isAnime(type, id)) ||
            AnimeContent.isAnimeRelease(sourceFilename, streamTitle)
    }
    val upscaler = mode.upscalerFor(isAnime)
    val smoothMotion = mode.smoothMotionFor(isAnime)
    // Set synchronously when the shader is already extracted, so the first load picks it up.
    remember(isAnime, upscaler, smoothMotion) {
        AnimePlayback.setCurrentIsAnime(isAnime)
        AnimePlayback.update(AnimePlaybackSettings(AnimeShaders.cachedPath(upscaler), smoothMotion))
    }
    LaunchedEffect(isAnime, upscaler, smoothMotion) {
        AnimePlayback.update(AnimePlaybackSettings(AnimeShaders.pathFor(upscaler), smoothMotion))
    }
    // Filler markers for the player's episode list / up-next, even when the details page was skipped.
    LaunchedEffect(meta?.id, mode.showFillerBadges) {
        if (meta != null && mode.showFillerBadges) AnimeFillerRepository.resolve(meta)
    }
    DisposableEffect(Unit) {
        onDispose {
            AnimePlayback.setCurrentIsAnime(false)
            AnimePlayback.update(AnimePlaybackSettings())
        }
    }
}
