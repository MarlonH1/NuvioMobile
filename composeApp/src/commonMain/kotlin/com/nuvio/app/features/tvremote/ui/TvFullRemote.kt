package com.nuvio.app.features.tvremote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Input
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioModalBottomSheet
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.features.tvremote.TvRemoteRepository
import com.nuvio.app.features.tvremote.protocol.RemoteKey
import com.nuvio.app.features.tvremote.protocol.RemoteLaunchTarget
import com.nuvio.app.features.tvremote.protocol.RemotePlaybackState
import com.nuvio.app.features.tvremote.protocol.RemoteSubtitleStyle
import kotlin.math.roundToInt
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** "Remote" and "Subtitle style" buttons at the bottom of the TV remote screen. */
@Composable
internal fun TvRemoteBottomBar(onOpenRemote: () -> Unit, onOpenSubtitleStyle: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = NuvioTokens.Space.s20, vertical = NuvioTokens.Space.s12),
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
    ) {
        BarPill(Icons.Rounded.SettingsRemote, stringResource(Res.string.tv_remote_full_remote), onOpenRemote, Modifier.weight(1f))
        BarPill(Icons.Rounded.FormatSize, stringResource(Res.string.tv_remote_subtitle_style), onOpenSubtitleStyle, Modifier.weight(1f))
    }
}

@Composable
private fun BarPill(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier) {
    Row(
        modifier = modifier
            .background(TvSheetColor.copy(alpha = 0.7f), RoundedCornerShape(24.dp))
            .tvGlass(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(vertical = NuvioTokens.Space.s14),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(NuvioTokens.Space.s8))
        Text(label, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

/** A full TV remote: power, home, settings, inputs, D-pad, back, volume, channels and media keys. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TvFullRemoteSheet(ambient: Color, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val session by TvRemoteRepository.session.collectAsStateWithLifecycle()
    var showApps by remember { mutableStateOf(false) }
    NuvioModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = TvSheetColor,
        contentColor = Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = NuvioTokens.Space.s20)
                .padding(bottom = NuvioTokens.Space.s24),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s16),
        ) {
            TvSheetHeader(
                icon = Icons.Rounded.SettingsRemote,
                title = stringResource(Res.string.tv_remote_full_remote),
                subtitle = null,
                ambient = ambient,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                RoundKey(Icons.Rounded.PowerSettingsNew, stringResource(Res.string.tv_remote_power), tint = Color(0xFFFF5C5C)) {
                    TvRemoteRepository.pressKey(RemoteKey.POWER)
                }
                RoundKey(Icons.Rounded.Home, stringResource(Res.string.tv_remote_home)) {
                    TvRemoteRepository.launchOnTv(RemoteLaunchTarget.HOME)
                }
                RoundKey(Icons.Rounded.Settings, stringResource(Res.string.tv_remote_settings)) {
                    TvRemoteRepository.launchOnTv(RemoteLaunchTarget.SETTINGS)
                }
                InputsKey()
            }

            DPad()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                RoundKey(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(Res.string.tv_remote_back)) {
                    TvRemoteRepository.pressKey(RemoteKey.BACK)
                }
                RoundKey(Icons.Rounded.Info, stringResource(Res.string.tv_remote_info)) {
                    TvRemoteRepository.pressKey(RemoteKey.INFO)
                }
                RoundKey(Icons.Rounded.Apps, stringResource(Res.string.tv_remote_apps)) {
                    TvRemoteRepository.requestApps()
                    showApps = true
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RockerKey(
                    label = stringResource(Res.string.tv_remote_volume),
                    onUp = TvRemoteRepository::volumeUp,
                    onDown = TvRemoteRepository::volumeDown,
                )
                RoundKey(
                    if (session.volume.muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                    stringResource(Res.string.tv_remote_mute_short),
                    active = session.volume.muted,
                ) { TvRemoteRepository.toggleMute() }
                RockerKey(
                    label = stringResource(Res.string.tv_remote_channel),
                    onUp = { TvRemoteRepository.pressKey(RemoteKey.CHANNEL_UP) },
                    onDown = { TvRemoteRepository.pressKey(RemoteKey.CHANNEL_DOWN) },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                RoundKey(Icons.Rounded.FastRewind, null) { TvRemoteRepository.pressKey(RemoteKey.REWIND) }
                RoundKey(Icons.Rounded.PlayArrow, null) { TvRemoteRepository.pressKey(RemoteKey.PLAY) }
                RoundKey(Icons.Rounded.Pause, null) { TvRemoteRepository.pressKey(RemoteKey.PAUSE) }
                RoundKey(Icons.Rounded.FastForward, null) { TvRemoteRepository.pressKey(RemoteKey.FAST_FORWARD) }
            }
        }
    }
    if (showApps) TvAppsSheet(ambient = ambient, onDismiss = { showApps = false })
}

@Composable
private fun RoundKey(
    icon: ImageVector,
    label: String?,
    size: Dp = 56.dp,
    tint: Color = Color.White,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(size)
                .tvGlass(CircleShape, selected = active)
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(size * 0.45f))
        }
        if (label != null) {
            Spacer(Modifier.height(NuvioTokens.Space.s4))
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun InputsKey() {
    var open by remember { mutableStateOf(false) }
    Box {
        RoundKey(Icons.Rounded.Input, stringResource(Res.string.tv_remote_inputs)) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            (1..4).forEach { index ->
                DropdownMenuItem(
                    text = { Text("HDMI $index") },
                    onClick = {
                        open = false
                        TvRemoteRepository.launchOnTv(RemoteLaunchTarget.hdmi(index))
                    },
                )
            }
        }
    }
}

/** Ring of arrows around a big OK button, like the TV remote's navigation wheel. */
@Composable
private fun DPad() {
    val haptic = LocalHapticFeedback.current
    val press = { key: String ->
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        TvRemoteRepository.pressKey(key)
    }
    Box(
        modifier = Modifier
            .size(236.dp)
            .tvGlass(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        ArrowKey(Icons.Rounded.KeyboardArrowUp, Modifier.align(Alignment.TopCenter)) { press(RemoteKey.UP) }
        ArrowKey(Icons.Rounded.KeyboardArrowDown, Modifier.align(Alignment.BottomCenter)) { press(RemoteKey.DOWN) }
        ArrowKey(Icons.Rounded.KeyboardArrowLeft, Modifier.align(Alignment.CenterStart)) { press(RemoteKey.LEFT) }
        ArrowKey(Icons.Rounded.KeyboardArrowRight, Modifier.align(Alignment.CenterEnd)) { press(RemoteKey.RIGHT) }
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(Color.White)
                .clickable { press(RemoteKey.OK) },
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(Res.string.tv_remote_ok), color = Color(0xFF0B0C0F), fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun ArrowKey(icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .size(72.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
    }
}

/** Vertical +/- pair (volume, channel). */
@Composable
private fun RockerKey(label: String, onUp: () -> Unit, onDown: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            modifier = Modifier
                .width(64.dp)
                .tvGlass(RoundedCornerShape(32.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onUp()
                    },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Add, contentDescription = null, tint = Color.White) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onDown()
                    },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Remove, contentDescription = null, tint = Color.White) }
        }
        Spacer(Modifier.height(NuvioTokens.Space.s4))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TvAppsSheet(ambient: Color, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val session by TvRemoteRepository.session.collectAsStateWithLifecycle()
    NuvioModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = TvSheetColor,
        contentColor = Color.White,
    ) {
        TvSheetHeader(icon = Icons.Rounded.Apps, title = stringResource(Res.string.tv_remote_apps), subtitle = null, ambient = ambient)
        if (session.apps.isEmpty()) {
            Text(
                stringResource(Res.string.tv_remote_apps_loading),
                modifier = Modifier.padding(NuvioTokens.Space.s20),
                color = Color.White.copy(alpha = 0.7f),
            )
        }
        LazyColumn(modifier = Modifier.heightIn(max = 520.dp)) {
            items(session.apps, key = { it.id }) { app ->
                Text(
                    text = app.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            TvRemoteRepository.launchOnTv(app.id)
                            onDismiss()
                        }
                        .padding(horizontal = NuvioTokens.Space.s20, vertical = NuvioTokens.Space.s14),
                    color = Color.White,
                )
            }
        }
        Spacer(Modifier.height(NuvioTokens.Space.s24))
    }
}

// Same choices as the TV's own subtitle style dialog.
private val SUBTITLE_TEXT_COLORS = listOf("#FFFFFF", "#D9D9D9", "#FFD700", "#00E5FF", "#FF5C5C", "#00FF88")
private val SUBTITLE_OUTLINE_COLORS = listOf("#000000", "#FFFFFF", "#00E5FF", "#FF5C5C")

private fun hexColor(hex: String): Color = Color(("FF" + hex.removePrefix("#")).toLong(16))

/** Subtitle size, colors, outline and position on the TV; changes apply live. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TvSubtitleStyleSheet(playback: RemotePlaybackState, ambient: Color, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val style = playback.subtitleStyle ?: RemoteSubtitleStyle()
    NuvioModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = TvSheetColor,
        contentColor = Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = NuvioTokens.Space.s24),
        ) {
            TvSheetHeader(
                icon = Icons.Rounded.FormatSize,
                title = stringResource(Res.string.tv_remote_subtitle_style),
                subtitle = stringResource(Res.string.tv_remote_subtitle_style_hint),
                ambient = ambient,
            )
            StyleSlider(
                label = stringResource(Res.string.tv_remote_sub_size),
                value = style.fontSize ?: 100,
                range = 50..200,
                step = 10,
                format = { "$it%" },
            ) { TvRemoteRepository.setSubtitleStyle(RemoteSubtitleStyle(fontSize = it)) }
            ColorRow(
                label = stringResource(Res.string.tv_remote_sub_color),
                colors = SUBTITLE_TEXT_COLORS,
                selected = style.textColor ?: "#FFFFFF",
            ) { TvRemoteRepository.setSubtitleStyle(RemoteSubtitleStyle(textColor = it)) }
            StyleSlider(
                label = stringResource(Res.string.tv_remote_sub_opacity),
                value = style.textOpacity ?: 100,
                range = 0..100,
                step = 10,
                format = { "$it%" },
            ) { TvRemoteRepository.setSubtitleStyle(RemoteSubtitleStyle(textOpacity = it)) }
            SwitchRow(stringResource(Res.string.tv_remote_sub_bold), style.bold ?: false) {
                TvRemoteRepository.setSubtitleStyle(RemoteSubtitleStyle(bold = it))
            }
            SwitchRow(stringResource(Res.string.tv_remote_sub_outline), style.outlineEnabled ?: true) {
                TvRemoteRepository.setSubtitleStyle(RemoteSubtitleStyle(outlineEnabled = it))
            }
            if (style.outlineEnabled != false) {
                ColorRow(
                    label = stringResource(Res.string.tv_remote_sub_outline_color),
                    colors = SUBTITLE_OUTLINE_COLORS,
                    selected = style.outlineColor ?: "#000000",
                ) { TvRemoteRepository.setSubtitleStyle(RemoteSubtitleStyle(outlineColor = it)) }
            }
            StyleSlider(
                label = stringResource(Res.string.tv_remote_sub_position),
                value = style.verticalOffset ?: 5,
                range = -20..50,
                step = 1,
                format = { "$it" },
            ) { TvRemoteRepository.setSubtitleStyle(RemoteSubtitleStyle(verticalOffset = it)) }
            Text(
                text = stringResource(Res.string.tv_remote_sub_reset),
                modifier = Modifier
                    .padding(horizontal = NuvioTokens.Space.s20, vertical = NuvioTokens.Space.s12)
                    .tvGlass(RoundedCornerShape(20.dp))
                    .clickable {
                        TvRemoteRepository.setSubtitleStyle(
                            RemoteSubtitleStyle(
                                fontSize = 100,
                                textColor = "#FFFFFF",
                                textOpacity = 100,
                                bold = false,
                                outlineEnabled = true,
                                outlineColor = "#000000",
                                verticalOffset = 5,
                            ),
                        )
                    }
                    .padding(horizontal = NuvioTokens.Space.s16, vertical = NuvioTokens.Space.s8),
                color = Color.White,
            )
        }
    }
}

/** Slider that only sends the value when the finger lifts, so the TV is not flooded. */
@Composable
private fun StyleSlider(label: String, value: Int, range: IntRange, step: Int, format: (Int) -> String, onChange: (Int) -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    var draft by remember { mutableFloatStateOf(value.toFloat()) }
    LaunchedEffect(value) { if (!dragging) draft = value.toFloat() }
    val shown = ((draft / step).roundToInt() * step).coerceIn(range.first, range.last)
    Column(modifier = Modifier.padding(horizontal = NuvioTokens.Space.s20, vertical = NuvioTokens.Space.s8)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Color.White, modifier = Modifier.weight(1f))
            Text(format(shown), color = Color.White.copy(alpha = 0.7f))
        }
        Slider(
            value = draft,
            onValueChange = {
                dragging = true
                draft = it
            },
            onValueChangeFinished = {
                dragging = false
                onChange(shown)
            },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = ((range.last - range.first) / step - 1).coerceAtLeast(0),
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorRow(label: String, colors: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = NuvioTokens.Space.s20, vertical = NuvioTokens.Space.s8)) {
        Text(label, color = Color.White)
        Spacer(Modifier.height(NuvioTokens.Space.s8))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12)) {
            colors.forEach { hex ->
                val isSelected = hex.equals(selected, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(hexColor(hex))
                        .border(if (isSelected) 3.dp else 1.dp, if (isSelected) Color.White else Color.White.copy(alpha = 0.3f), CircleShape)
                        .clickable { onSelect(hex) },
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = NuvioTokens.Space.s20, vertical = NuvioTokens.Space.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color.White, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
