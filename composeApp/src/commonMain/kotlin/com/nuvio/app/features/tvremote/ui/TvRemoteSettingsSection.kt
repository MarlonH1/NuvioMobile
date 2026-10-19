package com.nuvio.app.features.tvremote.ui

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.settings.SettingsGroup
import com.nuvio.app.features.settings.SettingsGroupDivider
import com.nuvio.app.features.settings.SettingsNavigationRow
import com.nuvio.app.features.settings.SettingsSection
import com.nuvio.app.features.settings.SettingsSwitchRow
import com.nuvio.app.features.tvremote.TvRemoteDestination
import com.nuvio.app.features.tvremote.TvRemoteNavigation
import com.nuvio.app.features.tvremote.TvRemoteRepository
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.tv_remote_enable_description
import nuvio.composeapp.generated.resources.tv_remote_manage_description_count
import nuvio.composeapp.generated.resources.tv_remote_manage_description_none
import nuvio.composeapp.generated.resources.tv_remote_manage_title
import nuvio.composeapp.generated.resources.tv_remote_open_remote
import nuvio.composeapp.generated.resources.tv_remote_playing_on
import nuvio.composeapp.generated.resources.tv_remote_title
import org.jetbrains.compose.resources.stringResource

/** "TV-Fernbedienung" block for the playback settings page. Off by default. */
internal fun LazyListScope.tvRemoteSettingsSection(isTablet: Boolean) {
    item(key = "tv_remote_settings") {
        remember { TvRemoteRepository.ensureLoaded() }
        val enabled by TvRemoteRepository.enabled.collectAsStateWithLifecycle()
        val tvs by TvRemoteRepository.pairedTvs.collectAsStateWithLifecycle()
        val session by TvRemoteRepository.session.collectAsStateWithLifecycle()
        val playingTvName = tvs.firstOrNull { it.id == session.tvId }?.name
            ?.takeIf { session.isPlaybackActive }

        SettingsSection(title = stringResource(Res.string.tv_remote_title), isTablet = isTablet) {
            SettingsGroup(isTablet = isTablet) {
                SettingsSwitchRow(
                    title = stringResource(Res.string.tv_remote_title),
                    description = stringResource(Res.string.tv_remote_enable_description),
                    checked = enabled,
                    isTablet = isTablet,
                    onCheckedChange = TvRemoteRepository::setEnabled,
                )
                if (enabled) {
                    SettingsGroupDivider(isTablet)
                    SettingsNavigationRow(
                        title = stringResource(Res.string.tv_remote_manage_title),
                        description = if (tvs.isEmpty()) {
                            stringResource(Res.string.tv_remote_manage_description_none)
                        } else {
                            stringResource(Res.string.tv_remote_manage_description_count, tvs.size)
                        },
                        icon = Icons.Rounded.Tv,
                        isTablet = isTablet,
                        onClick = { TvRemoteNavigation.open(TvRemoteDestination.Devices) },
                    )
                    if (playingTvName != null) {
                        SettingsGroupDivider(isTablet)
                        SettingsNavigationRow(
                            title = stringResource(Res.string.tv_remote_open_remote),
                            description = stringResource(Res.string.tv_remote_playing_on, playingTvName),
                            icon = Icons.Rounded.SettingsRemote,
                            isTablet = isTablet,
                            onClick = { TvRemoteNavigation.open(TvRemoteDestination.Control) },
                        )
                    }
                }
            }
        }
    }
}
