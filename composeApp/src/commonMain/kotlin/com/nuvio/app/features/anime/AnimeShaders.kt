package com.nuvio.app.features.anime

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import nuvio.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi

/** Extracts the bundled Anime4K shaders (MIT, bloc97) to disk so mpv can load them. */
object AnimeShaders {
    private val mutex = Mutex()
    private val paths = mutableMapOf<String, String>()

    @OptIn(ExperimentalResourceApi::class)
    suspend fun pathFor(mode: AnimeUpscalerMode): String? {
        val fileName = mode.shaderFile ?: return null
        return mutex.withLock {
            paths[fileName] ?: runCatching {
                AnimeModeStorage.writeShaderFile(fileName, Res.readBytes("files/anime4k/$fileName"))
            }.getOrNull()?.also { paths[fileName] = it }
        }
    }

    /** Already-extracted shader path, available without suspending. */
    fun cachedPath(mode: AnimeUpscalerMode): String? = mode.shaderFile?.let { paths[it] }

    /** The shader path to use for this playback, or null when upscaling should be off. */
    suspend fun activeShaderPath(isAnime: Boolean): String? =
        pathFor(AnimeModeRepository.snapshot().upscalerFor(isAnime))
}
