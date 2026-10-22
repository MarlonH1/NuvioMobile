package com.nuvio.app.features.tvremote.ui

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.nuvio.app.features.extras.ExtrasSettingsRepository
import com.nuvio.app.features.extras.RemoteButtonId
import com.nuvio.app.features.extras.normalizedRemoteButtons
import org.jetbrains.compose.resources.StringResource
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
import nuvio.composeapp.generated.resources.extras_move_down
import nuvio.composeapp.generated.resources.extras_move_up
import nuvio.composeapp.generated.resources.extras_player_controls_reset
import nuvio.composeapp.generated.resources.extras_save
import nuvio.composeapp.generated.resources.tv_remote_audio
import nuvio.composeapp.generated.resources.tv_remote_buttons_description
import nuvio.composeapp.generated.resources.tv_remote_buttons_title
import nuvio.composeapp.generated.resources.tv_remote_full_remote
import nuvio.composeapp.generated.resources.tv_remote_next_episode
import nuvio.composeapp.generated.resources.tv_remote_speed
import nuvio.composeapp.generated.resources.tv_remote_sources
import nuvio.composeapp.generated.resources.tv_remote_party_title
import nuvio.composeapp.generated.resources.tv_remote_stop
import nuvio.composeapp.generated.resources.tv_remote_subtitles
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
        var buttonsDialogOpen by remember { mutableStateOf(false) }
        if (buttonsDialogOpen) RemoteButtonsDialog(onDismiss = { buttonsDialogOpen = false })

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
                        title = stringResource(Res.string.tv_remote_buttons_title),
                        description = stringResource(Res.string.tv_remote_buttons_description),
                        icon = Icons.Rounded.Tune,
                        isTablet = isTablet,
                        onClick = { buttonsDialogOpen = true },
                    )
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

private fun remoteButtonLabel(id: RemoteButtonId): StringResource = when (id) {
    RemoteButtonId.Subtitles -> Res.string.tv_remote_subtitles
    RemoteButtonId.Audio -> Res.string.tv_remote_audio
    RemoteButtonId.Speed -> Res.string.tv_remote_speed
    RemoteButtonId.Sources -> Res.string.tv_remote_sources
    RemoteButtonId.Party -> Res.string.tv_remote_party_title
    RemoteButtonId.Remote -> Res.string.tv_remote_full_remote
    RemoteButtonId.Next -> Res.string.tv_remote_next_episode
    RemoteButtonId.Exit -> Res.string.tv_remote_stop
}

/** Order and on/off state of the buttons on the TV remote's now-playing screen. */
@Composable
internal fun RemoteButtonsDialog(onDismiss: () -> Unit) {
    remember { ExtrasSettingsRepository.reload() }
    val settings by ExtrasSettingsRepository.state.collectAsStateWithLifecycle()
    val buttons = normalizedRemoteButtons(settings.remoteButtons)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.tv_remote_buttons_title)) },
        text = {
            Column {
                buttons.forEachIndexed { index, entry ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(remoteButtonLabel(entry.id)), modifier = Modifier.weight(1f))
                        IconButton(
                            enabled = index > 0,
                            onClick = {
                                val list = buttons.toMutableList()
                                list.add(index - 1, list.removeAt(index))
                                ExtrasSettingsRepository.setRemoteButtons(list)
                            },
                        ) { Icon(Icons.Rounded.KeyboardArrowUp, stringResource(Res.string.extras_move_up)) }
                        IconButton(
                            enabled = index < buttons.lastIndex,
                            onClick = {
                                val list = buttons.toMutableList()
                                list.add(index + 1, list.removeAt(index))
                                ExtrasSettingsRepository.setRemoteButtons(list)
                            },
                        ) { Icon(Icons.Rounded.KeyboardArrowDown, stringResource(Res.string.extras_move_down)) }
                        Switch(
                            checked = entry.enabled,
                            onCheckedChange = { checked ->
                                ExtrasSettingsRepository.setRemoteButtons(
                                    buttons.map { if (it.id == entry.id) it.copy(enabled = checked) else it },
                                )
                            },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.extras_save)) } },
        dismissButton = {
            TextButton(onClick = ExtrasSettingsRepository::resetRemoteButtons) {
                Text(stringResource(Res.string.extras_player_controls_reset))
            }
        },
    )
}
