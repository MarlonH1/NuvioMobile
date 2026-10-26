package com.nuvio.app.features.tvremote.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.tvremote.PairedTv
import com.nuvio.app.features.tvremote.TvConnectionStatus
import com.nuvio.app.features.tvremote.TvRemoteDestination
import com.nuvio.app.features.tvremote.TvRemoteNavigation
import com.nuvio.app.features.tvremote.TvSessionState
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.tv_remote_active_target
import nuvio.composeapp.generated.resources.tv_remote_active_target_hint
import nuvio.composeapp.generated.resources.tv_remote_open_remote
import nuvio.composeapp.generated.resources.tv_remote_playing_on
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.text.style.TextAlign
import com.nuvio.app.features.tvremote.TvPairingState
import nuvio.composeapp.generated.resources.tv_remote_pair_connecting
import nuvio.composeapp.generated.resources.tv_remote_pair_done
import nuvio.composeapp.generated.resources.tv_remote_pair_enter_code
import nuvio.composeapp.generated.resources.tv_remote_pair_failed
import nuvio.composeapp.generated.resources.tv_remote_pair_title
import nuvio.composeapp.generated.resources.tv_remote_pair_title_named
import nuvio.composeapp.generated.resources.tv_remote_pair_verifying
import nuvio.composeapp.generated.resources.tv_remote_pair_wrong_code
import org.jetbrains.compose.resources.stringResource

/** Big card at the top of the TV list for the selected playback target. */
@Composable
internal fun ActiveTvHero(tv: PairedTv, session: TvSessionState) {
    val tokens = MaterialTheme.nuvio
    val connected = session.tvId == tv.id && session.connection == TvConnectionStatus.Connected
    val glow = if (connected) tokens.colors.accent else tokens.colors.textMuted
    val media = session.playback.media?.takeIf { session.isPlaybackActive }
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xFF0C0D10))
            .background(
                Brush.linearGradient(
                    listOf(glow.copy(alpha = 0.28f), Color.Transparent, glow.copy(alpha = 0.08f)),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
            .padding(NuvioTokens.Space.s20),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TvIllustration(glow = glow, connected = connected)
            Spacer(Modifier.width(NuvioTokens.Space.s16))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.tv_remote_active_target).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = Color.White.copy(alpha = 0.55f),
                )
                Text(
                    text = tv.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PulsingDot(color = connectionColor(session.connection), pulsing = connected, size = 7.dp)
                    Text(
                        text = tvStatusText(tv, session, isActive = true),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.height(NuvioTokens.Space.s16))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .background(Color.White)
                .clickable { TvRemoteNavigation.open(TvRemoteDestination.Control) }
                .padding(horizontal = NuvioTokens.Space.s16, vertical = NuvioTokens.Space.s12),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.SettingsRemote,
                contentDescription = null,
                tint = Color(0xFF0B0C0F),
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(NuvioTokens.Space.s8))
            Text(
                text = stringResource(Res.string.tv_remote_open_remote),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0B0C0F),
            )
        }
        Text(
            text = media?.let { stringResource(Res.string.tv_remote_playing_on, tv.name) + " · " + it.title }
                ?: stringResource(Res.string.tv_remote_active_target_hint),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.5f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = NuvioTokens.Space.s10),
        )
    }
}

/** Stylized TV with a glowing screen; brighter when connected. */
@Composable
internal fun TvIllustration(glow: Color, connected: Boolean) {
    val screen by animateColorAsState(if (connected) glow else Color.White.copy(alpha = 0.15f), label = "tv_screen")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .width(72.dp)
                .aspectRatio(16f / 10f)
                .tvGlow(glow, radiusFraction = 0.8f, alpha = if (connected) 0.45f else 0.1f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF15171C))
                .border(2.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(8.dp))
                .padding(4.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    Brush.linearGradient(listOf(screen.copy(alpha = 0.85f), screen.copy(alpha = 0.25f))),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Tv,
                contentDescription = null,
                tint = Color.White.copy(alpha = if (connected) 0.9f else 0.4f),
                modifier = Modifier.size(18.dp),
            )
        }
        Box(
            modifier = Modifier
                .padding(top = 3.dp)
                .width(26.dp)
                .height(3.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.25f)),
        )
    }
}

/** Six separate digit boxes backed by one hidden text field. */
@Composable
internal fun PairingCodeInput(code: String, enabled: Boolean, error: Boolean, onCodeChange: (String) -> Unit) {
    val tokens = MaterialTheme.nuvio
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(enabled) { if (enabled) runCatching { focusRequester.requestFocus() } }
    BasicTextField(
        value = code,
        onValueChange = { value -> onCodeChange(value.filter(Char::isDigit).take(6)) },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        decorationBox = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
            ) {
                repeat(6) { index ->
                    val char = code.getOrNull(index)
                    val focused = enabled && index == code.length.coerceAtMost(5)
                    val border = when {
                        error -> tokens.colors.danger
                        focused -> tokens.colors.accent
                        char != null -> Color.White.copy(alpha = 0.35f)
                        else -> Color.White.copy(alpha = 0.12f)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(0.8f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(tokens.colors.surfaceCard)
                            .border(if (focused) 2.dp else 1.dp, border, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = char?.toString() ?: "",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = tokens.colors.textPrimary,
                        )
                    }
                }
            }
        },
    )
}

/** Body of the pairing dialog: illustration, title and the state-specific content. */
@Composable
internal fun PairingDialogContent(state: TvPairingState, code: String, onCodeChange: (String) -> Unit) {
    val tokens = MaterialTheme.nuvio
    val success = state is TvPairingState.Paired
    val failed = state is TvPairingState.Failed
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.padding(vertical = NuvioTokens.Space.s12), contentAlignment = Alignment.Center) {
            TvIllustration(
                glow = when {
                    success -> tokens.colors.success
                    failed -> tokens.colors.danger
                    else -> tokens.colors.accent
                },
                connected = state !is TvPairingState.Connecting && !failed,
            )
            if (success || failed) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 4.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(if (success) tokens.colors.success else tokens.colors.danger)
                        .border(3.dp, tokens.colors.surfaceDialog, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (success) Icons.Rounded.Check else Icons.Rounded.Close,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        Text(
            text = when (state) {
                is TvPairingState.AwaitingCode -> stringResource(Res.string.tv_remote_pair_title_named, state.tvName)
                is TvPairingState.Paired -> stringResource(Res.string.tv_remote_pair_done, state.tv.name)
                else -> stringResource(Res.string.tv_remote_pair_title)
            },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = tokens.colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(NuvioTokens.Space.s6))
        val message = when (state) {
            TvPairingState.Connecting -> stringResource(Res.string.tv_remote_pair_connecting)
            is TvPairingState.AwaitingCode -> stringResource(
                if (state.wrongCode) Res.string.tv_remote_pair_wrong_code else Res.string.tv_remote_pair_enter_code,
            )
            TvPairingState.Verifying -> stringResource(Res.string.tv_remote_pair_verifying)
            is TvPairingState.Failed -> stringResource(Res.string.tv_remote_pair_failed)
            else -> null
        }
        if (message != null) {
            val wrong = (state as? TvPairingState.AwaitingCode)?.wrongCode == true
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (wrong || failed) tokens.colors.danger else tokens.colors.textMuted,
                textAlign = TextAlign.Center,
            )
        }
        when (state) {
            TvPairingState.Connecting -> {
                Spacer(Modifier.height(NuvioTokens.Space.s16))
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 3.dp,
                    color = tokens.colors.accent,
                )
            }
            is TvPairingState.AwaitingCode, TvPairingState.Verifying -> {
                Spacer(Modifier.height(NuvioTokens.Space.s16))
                PairingCodeInput(
                    code = code,
                    enabled = state is TvPairingState.AwaitingCode,
                    error = (state as? TvPairingState.AwaitingCode)?.wrongCode == true && code.isEmpty(),
                    onCodeChange = onCodeChange,
                )
            }
            else -> Unit
        }
        Spacer(Modifier.height(NuvioTokens.Space.s8))
    }
}
