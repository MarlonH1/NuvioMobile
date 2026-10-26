package com.nuvio.app.features.tvremote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtonStyle
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.core.ui.NuvioInputField
import com.nuvio.app.core.ui.NuvioScreen
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.settings.SettingsGroup
import com.nuvio.app.features.settings.SettingsGroupDivider
import com.nuvio.app.features.settings.SettingsNavigationRow
import com.nuvio.app.features.settings.SettingsSection
import com.nuvio.app.features.settings.SettingsSwitchRow
import com.nuvio.app.features.tvremote.DEMO_TV_CODE
import com.nuvio.app.features.tvremote.PairedTv
import com.nuvio.app.features.tvremote.TV_REMOTE_DEFAULT_PORT
import com.nuvio.app.features.tvremote.TvPairingState
import com.nuvio.app.features.tvremote.TvRemoteLog
import com.nuvio.app.features.tvremote.TvRemoteRepository
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_cancel
import nuvio.composeapp.generated.resources.action_close
import nuvio.composeapp.generated.resources.tv_remote_add_by_ip
import nuvio.composeapp.generated.resources.tv_remote_add_by_ip_description
import nuvio.composeapp.generated.resources.tv_remote_add_demo
import nuvio.composeapp.generated.resources.tv_remote_add_demo_description
import nuvio.composeapp.generated.resources.tv_remote_clear
import nuvio.composeapp.generated.resources.tv_remote_connect
import nuvio.composeapp.generated.resources.tv_remote_debug_log
import nuvio.composeapp.generated.resources.tv_remote_debug_log_description
import nuvio.composeapp.generated.resources.tv_remote_debug_log_empty
import nuvio.composeapp.generated.resources.tv_remote_discovery_unavailable
import nuvio.composeapp.generated.resources.tv_remote_enable_description
import nuvio.composeapp.generated.resources.tv_remote_ip_hint
import nuvio.composeapp.generated.resources.tv_remote_ip_invalid
import nuvio.composeapp.generated.resources.tv_remote_pair_confirm
import nuvio.composeapp.generated.resources.tv_remote_pair_connecting
import nuvio.composeapp.generated.resources.tv_remote_pair_done
import nuvio.composeapp.generated.resources.tv_remote_pair_enter_code
import nuvio.composeapp.generated.resources.tv_remote_pair_failed
import nuvio.composeapp.generated.resources.tv_remote_pair_title
import nuvio.composeapp.generated.resources.tv_remote_pair_title_named
import nuvio.composeapp.generated.resources.tv_remote_pair_verifying
import nuvio.composeapp.generated.resources.tv_remote_pair_wrong_code
import nuvio.composeapp.generated.resources.tv_remote_remove
import nuvio.composeapp.generated.resources.tv_remote_remove_confirm_message
import nuvio.composeapp.generated.resources.tv_remote_remove_confirm_title
import nuvio.composeapp.generated.resources.tv_remote_repair
import nuvio.composeapp.generated.resources.tv_remote_retry
import nuvio.composeapp.generated.resources.tv_remote_section_add
import nuvio.composeapp.generated.resources.tv_remote_section_debug
import nuvio.composeapp.generated.resources.tv_remote_section_discovered
import nuvio.composeapp.generated.resources.tv_remote_discovery_searching
import nuvio.composeapp.generated.resources.tv_remote_section_paired
import nuvio.composeapp.generated.resources.tv_remote_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun TvRemoteDevicesScreen(
    title: String,
    isTablet: Boolean,
    onBack: () -> Unit,
) {
    LaunchedEffect(Unit) { TvRemoteRepository.ensureLoaded() }
    val enabled by TvRemoteRepository.enabled.collectAsStateWithLifecycle()
    val tvs by TvRemoteRepository.pairedTvs.collectAsStateWithLifecycle()
    val discovered by TvRemoteRepository.discoveredTvs.collectAsStateWithLifecycle()
    DisposableEffect(Unit) {
        TvRemoteRepository.startDiscovery()
        onDispose { TvRemoteRepository.stopDiscovery() }
    }
    val activeTvId by TvRemoteRepository.activeTvId.collectAsStateWithLifecycle()
    val session by TvRemoteRepository.session.collectAsStateWithLifecycle()
    val pairing by TvRemoteRepository.pairing.collectAsStateWithLifecycle()
    var showIpDialog by rememberSaveable { mutableStateOf(false) }
    var showLog by rememberSaveable { mutableStateOf(false) }
    var removeCandidate by remember { mutableStateOf<PairedTv?>(null) }

    NuvioScreen {
        item {
            NuvioScreenHeader(title = title, onBack = onBack)
        }
        item {
            SettingsGroup(isTablet = isTablet) {
                SettingsSwitchRow(
                    title = stringResource(Res.string.tv_remote_title),
                    description = stringResource(Res.string.tv_remote_enable_description),
                    checked = enabled,
                    isTablet = isTablet,
                    onCheckedChange = TvRemoteRepository::setEnabled,
                )
            }
        }
        if (enabled) {
            tvs.firstOrNull { it.id == activeTvId }?.let { activeTv ->
                item(key = "tv_remote_hero") { ActiveTvHero(tv = activeTv, session = session) }
            }
            if (tvs.isNotEmpty()) {
                item {
                    SettingsSection(title = stringResource(Res.string.tv_remote_section_paired), isTablet = isTablet) {
                        SettingsGroup(isTablet = isTablet) {
                            tvs.forEachIndexed { index, tv ->
                                if (index > 0) SettingsGroupDivider(isTablet)
                                PairedTvRow(
                                    tv = tv,
                                    isActive = tv.id == activeTvId,
                                    status = tvStatusText(tv, session, tv.id == activeTvId),
                                    onSelect = {
                                        TvRemoteRepository.selectTarget(if (tv.id == activeTvId) null else tv.id)
                                    },
                                    onRepair = {
                                        TvRemoteRepository.startPairing(if (tv.demo) "demo" else tv.host, tv.port)
                                    },
                                    onRemove = { removeCandidate = tv },
                                )
                            }
                        }
                    }
                }
            }
            item {
                SettingsSection(title = stringResource(Res.string.tv_remote_section_discovered), isTablet = isTablet) {
                    SettingsGroup(isTablet = isTablet) {
                        discovered.filterNot { found -> tvs.any { it.id == found.id } }.forEach { found ->
                            SettingsNavigationRow(
                                title = found.name,
                                description = found.host,
                                icon = Icons.Rounded.Tv,
                                isTablet = isTablet,
                                onClick = { TvRemoteRepository.startPairing(found.host, found.port) },
                            )
                            SettingsGroupDivider(isTablet)
                        }
                        Text(
                            text = stringResource(
                                if (TvRemoteRepository.isDiscoverySupported) {
                                    Res.string.tv_remote_discovery_searching
                                } else {
                                    Res.string.tv_remote_discovery_unavailable
                                },
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.nuvio.colors.textMuted,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        )
                    }
                }
            }
            item {
                SettingsSection(title = stringResource(Res.string.tv_remote_section_add), isTablet = isTablet) {
                    SettingsGroup(isTablet = isTablet) {
                        SettingsNavigationRow(
                            title = stringResource(Res.string.tv_remote_add_by_ip),
                            description = stringResource(Res.string.tv_remote_add_by_ip_description),
                            icon = Icons.Rounded.Add,
                            isTablet = isTablet,
                            onClick = { showIpDialog = true },
                        )
                        SettingsGroupDivider(isTablet)
                        SettingsNavigationRow(
                            title = stringResource(Res.string.tv_remote_add_demo),
                            description = stringResource(Res.string.tv_remote_add_demo_description, DEMO_TV_CODE),
                            icon = Icons.Rounded.Science,
                            isTablet = isTablet,
                            onClick = TvRemoteRepository::addDemoTv,
                        )
                    }
                }
            }
            item {
                SettingsSection(title = stringResource(Res.string.tv_remote_section_debug), isTablet = isTablet) {
                    SettingsGroup(isTablet = isTablet) {
                        SettingsNavigationRow(
                            title = stringResource(Res.string.tv_remote_debug_log),
                            description = stringResource(Res.string.tv_remote_debug_log_description),
                            isTablet = isTablet,
                            onClick = { showLog = !showLog },
                        )
                        if (showLog) {
                            SettingsGroupDivider(isTablet)
                            DebugLog()
                        }
                    }
                }
            }
        }
    }

    if (showIpDialog) {
        AddByIpDialog(
            onDismiss = { showIpDialog = false },
            onConnect = { host, port ->
                showIpDialog = false
                TvRemoteRepository.startPairing(host, port)
            },
        )
    }
    if (pairing !is TvPairingState.Idle) {
        PairingDialog(state = pairing)
    }
    removeCandidate?.let { tv ->
        DialogSurface(
            onDismissRequest = { removeCandidate = null },
            title = stringResource(Res.string.tv_remote_remove_confirm_title, tv.name),
            message = stringResource(Res.string.tv_remote_remove_confirm_message),
        ) {
            DialogButtons {
                DialogButton(text = stringResource(Res.string.action_cancel), onClick = { removeCandidate = null })
                DialogButton(
                    text = stringResource(Res.string.tv_remote_remove),
                    style = DialogButtonStyle.Destructive,
                    onClick = {
                        TvRemoteRepository.removeTv(tv.id)
                        removeCandidate = null
                    },
                )
            }
        }
    }
}

@Composable
private fun PairedTvRow(
    tv: PairedTv,
    isActive: Boolean,
    status: String,
    onSelect: () -> Unit,
    onRepair: () -> Unit,
    onRemove: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(tokens.shapes.compactCard)
                .background(tokens.colors.accent.copy(alpha = if (isActive) 0.9f else tokens.opacity.selected)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Tv,
                contentDescription = null,
                tint = if (isActive) tokens.colors.onAccent else tokens.colors.accent,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                text = tv.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = tokens.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isActive) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = tokens.colors.accent,
                modifier = Modifier.size(22.dp),
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = null, tint = tokens.colors.textMuted)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.tv_remote_repair)) },
                    onClick = {
                        menuOpen = false
                        onRepair()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.tv_remote_remove), color = tokens.colors.danger) },
                    onClick = {
                        menuOpen = false
                        onRemove()
                    },
                )
            }
        }
    }
}

@Composable
private fun DebugLog() {
    val entries by TvRemoteLog.entries.collectAsStateWithLifecycle()
    val tokens = MaterialTheme.nuvio
    Column(modifier = Modifier.padding(16.dp)) {
        if (entries.isEmpty()) {
            Text(
                text = stringResource(Res.string.tv_remote_debug_log_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.colors.textMuted,
            )
        } else {
            entries.takeLast(80).asReversed().forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                    color = if (" W " in line) tokens.colors.warning else tokens.colors.textSecondary,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.tv_remote_clear),
                style = MaterialTheme.typography.labelLarge,
                color = tokens.colors.accent,
                modifier = Modifier
                    .clip(tokens.shapes.chip)
                    .clickable(onClick = TvRemoteLog::clear)
                    .padding(vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun AddByIpDialog(onDismiss: () -> Unit, onConnect: (String, Int) -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    DialogSurface(
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.tv_remote_add_by_ip),
        message = stringResource(Res.string.tv_remote_add_by_ip_description),
    ) {
        NuvioInputField(
            value = input,
            onValueChange = {
                input = it
                invalid = false
            },
            placeholder = stringResource(Res.string.tv_remote_ip_hint),
        )
        if (invalid) {
            Text(
                text = stringResource(Res.string.tv_remote_ip_invalid),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.nuvio.colors.danger,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        DialogButtons {
            DialogButton(text = stringResource(Res.string.action_cancel), onClick = onDismiss)
            DialogButton(
                text = stringResource(Res.string.tv_remote_connect),
                style = DialogButtonStyle.Primary,
                enabled = input.isNotBlank(),
                onClick = {
                    val parsed = parseTvAddress(input)
                    if (parsed == null) invalid = true else onConnect(parsed.first, parsed.second)
                },
            )
        }
    }
}

@Composable
private fun PairingDialog(state: TvPairingState) {
    var code by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(state) {
        if (state is TvPairingState.AwaitingCode && state.wrongCode) code = ""
    }
    DialogSurface(onDismissRequest = TvRemoteRepository::cancelPairing) {
        PairingDialogContent(
            state = state,
            code = code,
            onCodeChange = { value ->
                code = value
                if (value.length == 6) TvRemoteRepository.confirmPairingCode(value)
            },
        )
        PairingDialogButtons(state, code)
    }
}

/** Accepts `host`, `host:port`, or `[ipv6]:port`. */
internal fun parseTvAddress(input: String): Pair<String, Int>? {
    val text = input.trim().removePrefix("ws://").removePrefix("http://").substringBefore('/')
    if (text.isEmpty()) return null
    val (host, portText) = when {
        text.startsWith("[") -> text.substringAfter('[').substringBefore(']') to text.substringAfter("]:", "")
        text.count { it == ':' } == 1 -> text.substringBefore(':') to text.substringAfter(':')
        else -> text to ""
    }
    if (host.isBlank() || host.any { it.isWhitespace() }) return null
    val port = if (portText.isEmpty()) TV_REMOTE_DEFAULT_PORT else portText.toIntOrNull() ?: return null
    if (port !in 1..65535) return null
    return host to port
}

@Composable
internal fun PairingDialogButtons(state: TvPairingState, code: String) {
    DialogButtons {
        when (state) {
            is TvPairingState.Paired -> DialogButton(
                text = stringResource(Res.string.action_close),
                style = DialogButtonStyle.Primary,
                onClick = TvRemoteRepository::cancelPairing,
            )
            is TvPairingState.Failed -> {
                DialogButton(text = stringResource(Res.string.action_cancel), onClick = TvRemoteRepository::cancelPairing)
                DialogButton(
                    text = stringResource(Res.string.tv_remote_retry),
                    style = DialogButtonStyle.Primary,
                    onClick = TvRemoteRepository::retryPairing,
                )
            }
            is TvPairingState.AwaitingCode, TvPairingState.Verifying -> {
                DialogButton(text = stringResource(Res.string.action_cancel), onClick = TvRemoteRepository::cancelPairing)
                DialogButton(
                    text = stringResource(Res.string.tv_remote_pair_confirm),
                    style = DialogButtonStyle.Primary,
                    enabled = code.length == 6,
                    loading = state is TvPairingState.Verifying,
                    onClick = { TvRemoteRepository.confirmPairingCode(code) },
                )
            }
            else -> DialogButton(
                text = stringResource(Res.string.action_cancel),
                onClick = TvRemoteRepository::cancelPairing,
            )
        }
    }
}
