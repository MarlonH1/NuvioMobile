package com.nuvio.app.features.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.anime.AnimeEnhancementScope
import com.nuvio.app.features.anime.AnimeModeRepository
import com.nuvio.app.features.anime.AnimePlayback
import com.nuvio.app.features.anime.AnimeUpscalerMode

/**
 * In-player Anime Enhancements (ported from NuvioCodeineXO's Enhancements menu): switch the
 * Anime4K preset and smooth motion on the fly. Changes apply to the running video immediately.
 */
@Composable
internal fun AnimeEnhancementsPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by remember {
        AnimeModeRepository.ensureLoaded()
        AnimeModeRepository.state
    }.collectAsStateWithLifecycle()
    val isAnime by AnimePlayback.currentIsAnime.collectAsStateWithLifecycle()

    PlayerSidePanel(visible = visible, onDismiss = onDismiss, modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            PlayerPanelHeader(title = "Anime Enhancements") {
                PlayerDialogButton(label = "Close", onClick = onDismiss)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (isAnime) "This title is detected as anime." else "This title isn't detected as anime.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                OptionGroup(
                    title = "Anime4K Upscaling",
                    options = AnimeUpscalerMode.entries,
                    selected = state.upscaler,
                    label = { it.label },
                    description = {
                        when (it) {
                            AnimeUpscalerMode.OFF -> "No upscaling shaders"
                            AnimeUpscalerMode.FAST -> "Light; laptops and weaker GPUs"
                            AnimeUpscalerMode.LINE_RECOVERY -> "Smooth lines, balanced detail"
                            AnimeUpscalerMode.SHARP_DETAIL -> "Maximum clarity; needs a strong GPU"
                        }
                    },
                    onSelect = AnimeModeRepository::setUpscaler,
                )
                OptionGroup(
                    title = "Upscale",
                    options = listOf(AnimeEnhancementScope.ANIME_ONLY, AnimeEnhancementScope.ALL_MEDIA),
                    selected = state.upscalerScope.takeIf { it != AnimeEnhancementScope.OFF }
                        ?: AnimeEnhancementScope.ANIME_ONLY,
                    label = { it.label },
                    onSelect = AnimeModeRepository::setUpscalerScope,
                )
                OptionGroup(
                    title = "Smooth Motion",
                    options = AnimeEnhancementScope.entries,
                    selected = state.smoothMotion,
                    label = { it.label },
                    description = {
                        when (it) {
                            AnimeEnhancementScope.OFF -> "Play at the source frame rate"
                            AnimeEnhancementScope.ANIME_ONLY -> "Smoother pans for anime and cartoons"
                            AnimeEnhancementScope.ALL_MEDIA -> "Smoother motion for everything"
                        }
                    },
                    onSelect = AnimeModeRepository::setSmoothMotion,
                )
                Text(
                    text = "Smooth motion retimes video to your display's refresh rate and blends frames " +
                        "(mpv interpolation). These enhancements need the mpv player; on Android choose " +
                        "the libmpv engine in Playback settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
