package com.nuvio.app.features.anime

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.settings.SettingsGroup
import com.nuvio.app.features.settings.SettingsGroupDivider
import com.nuvio.app.features.settings.SettingsNavigationRow
import com.nuvio.app.features.settings.SettingsSection
import com.nuvio.app.features.settings.SettingsSwitchRow

/** Anime enhancements ported from NuvioCodeineXO (filler markers, Anime4K, smooth motion). */
internal fun LazyListScope.animeModeSettingsSection(isTablet: Boolean, showUpscaler: Boolean) {
    item {
        val state by remember {
            AnimeModeRepository.ensureLoaded()
            AnimeModeRepository.state
        }.collectAsStateWithLifecycle()
        SettingsSection(title = "ANIME", isTablet = isTablet) {
            SettingsGroup(isTablet = isTablet) {
                SettingsSwitchRow(
                    title = "Filler Markers",
                    description = "Mark filler and mixed-canon anime episodes (AnimeFillerList / MyAnimeList).",
                    checked = state.fillerBadges,
                    isTablet = isTablet,
                    onCheckedChange = AnimeModeRepository::setFillerBadges,
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = "Hide Filler Episodes",
                    description = "Leave pure filler episodes out of episode lists and up-next.",
                    checked = state.hideFillers,
                    enabled = state.fillerBadges,
                    isTablet = isTablet,
                    onCheckedChange = AnimeModeRepository::setHideFillers,
                )
                if (showUpscaler) {
                    SettingsGroupDivider(isTablet = isTablet)
                    SettingsNavigationRow(
                        title = "Anime4K Upscaling",
                        description = "Sharpens video in the mpv player. Tap to switch preset; heavier presets need a stronger GPU. Also adjustable in the player.",
                        isTablet = isTablet,
                        trailingContent = {
                            Text(
                                text = state.upscaler.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        },
                        onClick = {
                            val modes = AnimeUpscalerMode.entries
                            AnimeModeRepository.setUpscaler(modes[(state.upscaler.ordinal + 1) % modes.size])
                        },
                    )
                    SettingsGroupDivider(isTablet = isTablet)
                    SettingsNavigationRow(
                        title = "Upscale",
                        description = "Which titles get Anime4K. Tap to switch.",
                        isTablet = isTablet,
                        trailingContent = { ValueLabel(state.upscalerScope.label) },
                        onClick = {
                            AnimeModeRepository.setUpscalerScope(
                                if (state.upscalerScope == AnimeEnhancementScope.ALL_MEDIA) AnimeEnhancementScope.ANIME_ONLY
                                else AnimeEnhancementScope.ALL_MEDIA,
                            )
                        },
                    )
                    SettingsGroupDivider(isTablet = isTablet)
                    SettingsNavigationRow(
                        title = "Smooth Motion",
                        description = "Retimes video to your display's refresh rate and blends frames for smoother pans. Tap to switch.",
                        isTablet = isTablet,
                        trailingContent = { ValueLabel(state.smoothMotion.label) },
                        onClick = {
                            val scopes = AnimeEnhancementScope.entries
                            AnimeModeRepository.setSmoothMotion(scopes[(state.smoothMotion.ordinal + 1) % scopes.size])
                        },
                    )
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun ValueLabel(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
}
