package com.nuvio.app.features.tvremote.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioModalBottomSheet
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.features.extras.PartyFriendsRepository
import com.nuvio.app.features.extras.displayName
import com.nuvio.app.features.extras.formatFriendCode
import com.nuvio.app.features.tvremote.TvPartyInviteStatus
import com.nuvio.app.features.tvremote.TvRemoteRepository
import com.nuvio.app.features.tvremote.protocol.RemoteParty
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.tv_remote_party_code
import nuvio.composeapp.generated.resources.tv_remote_party_failed
import nuvio.composeapp.generated.resources.tv_remote_party_friends
import nuvio.composeapp.generated.resources.tv_remote_party_friends_hint
import nuvio.composeapp.generated.resources.tv_remote_party_full
import nuvio.composeapp.generated.resources.tv_remote_party_start_failed
import nuvio.composeapp.generated.resources.tv_remote_party_not_started
import nuvio.composeapp.generated.resources.tv_remote_party_host
import nuvio.composeapp.generated.resources.tv_remote_party_invite
import nuvio.composeapp.generated.resources.tv_remote_party_members
import nuvio.composeapp.generated.resources.tv_remote_party_no_friends
import nuvio.composeapp.generated.resources.tv_remote_party_sending
import nuvio.composeapp.generated.resources.tv_remote_party_sent
import nuvio.composeapp.generated.resources.tv_remote_party_summary
import nuvio.composeapp.generated.resources.tv_remote_party_this_tv
import nuvio.composeapp.generated.resources.tv_remote_party_title
import nuvio.composeapp.generated.resources.tv_remote_party_update_tv
import org.jetbrains.compose.resources.stringResource

internal fun RemoteParty.displayTitle(): String =
    if (season != null && episode != null) "$title · S$season E$episode" else title

private val RemoteParty.isFull: Boolean get() = maxMembers > 0 && members.size >= maxMembers

/** Compact banner under the top bar while the TV is in a watch party. */
@Composable
internal fun TvPartyStrip(party: RemoteParty, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .tvGlass(shape)
            .clip(shape)
            .clickable(onClick = onOpen)
            .padding(horizontal = NuvioTokens.Space.s14, vertical = NuvioTokens.Space.s10),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Groups, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(NuvioTokens.Space.s12))
        Column(Modifier.weight(1f)) {
            Text(
                text = "${stringResource(Res.string.tv_remote_party_title)} · ${party.code}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(
                text = stringResource(Res.string.tv_remote_party_summary, party.members.size, party.maxMembers, party.displayTitle()),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!party.isFull) {
            Spacer(Modifier.width(NuvioTokens.Space.s8))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.PersonAdd, contentDescription = null, tint = remoteAccent(), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(NuvioTokens.Space.s4))
                Text(
                    text = stringResource(Res.string.tv_remote_party_invite),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = remoteAccent(),
                )
            }
        }
    }
}

/**
 * Members of the TV's party plus the phone's friend list, each invitable through the TV. Without
 * a party ([party] null) only the friends are shown; inviting one starts the party on the TV.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TvPartySheet(
    party: RemoteParty?,
    invites: Map<String, TvPartyInviteStatus>,
    ambient: Color,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(Unit) { PartyFriendsRepository.reload() }
    val friends by PartyFriendsRepository.friends.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    NuvioModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = TvSheetColor,
        contentColor = Color.White,
    ) {
        TvSheetHeader(
            icon = Icons.Rounded.Groups,
            title = stringResource(Res.string.tv_remote_party_title),
            subtitle = if (party != null) {
                "${stringResource(Res.string.tv_remote_party_code, party.code)} · ${party.displayTitle()}"
            } else {
                stringResource(Res.string.tv_remote_party_not_started)
            },
            ambient = ambient,
        )
        Column(
            modifier = Modifier
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = NuvioTokens.Space.s12)
                .padding(bottom = NuvioTokens.Space.s16),
            verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
        ) {
            val full = party?.isFull == true
            if (party != null) SectionLabel(stringResource(Res.string.tv_remote_party_members) + " · ${party.members.size}/${party.maxMembers}")
            party?.members.orEmpty().forEach { member ->
                val roles = listOfNotNull(
                    stringResource(Res.string.tv_remote_party_host).takeIf { member.host },
                    stringResource(Res.string.tv_remote_party_this_tv).takeIf { member.self },
                )
                TvOptionRow(
                    title = member.name,
                    subtitle = roles.joinToString(" · ").ifEmpty { null },
                    icon = when {
                        member.self -> Icons.Rounded.Tv
                        member.host -> Icons.Rounded.Star
                        else -> Icons.Rounded.Person
                    },
                    selected = false,
                    ambient = ambient,
                    onClick = {},
                )
            }

            SectionLabel(stringResource(Res.string.tv_remote_party_friends))
            Hint(
                stringResource(
                    when {
                        full -> Res.string.tv_remote_party_full
                        friends.isEmpty() -> Res.string.tv_remote_party_no_friends
                        else -> Res.string.tv_remote_party_friends_hint
                    },
                ),
            )
            friends.forEach { friend ->
                val status = invites[friend.code]
                val busy = status == TvPartyInviteStatus.Sending || status == TvPartyInviteStatus.Sent
                TvOptionRow(
                    title = friend.displayName,
                    subtitle = when (status) {
                        TvPartyInviteStatus.Sending -> stringResource(Res.string.tv_remote_party_sending)
                        TvPartyInviteStatus.Sent -> stringResource(Res.string.tv_remote_party_sent)
                        is TvPartyInviteStatus.Failed -> stringResource(
                            when (status.reason) {
                                "unknown_type" -> Res.string.tv_remote_party_update_tv
                                "no_party" -> Res.string.tv_remote_party_start_failed
                                else -> Res.string.tv_remote_party_failed
                            },
                        )
                        null -> formatFriendCode(friend.code)
                    },
                    icon = Icons.Rounded.Person,
                    selected = status == TvPartyInviteStatus.Sent,
                    ambient = ambient,
                    trailing = {
                        when (status) {
                            TvPartyInviteStatus.Sending -> CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp),
                            )
                            TvPartyInviteStatus.Sent -> Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White)
                            else -> if (!full) {
                                Text(
                                    text = stringResource(Res.string.tv_remote_party_invite),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = remoteAccent(),
                                )
                            }
                        }
                    },
                    onClick = {
                        if (!busy && !full) TvRemoteRepository.inviteToTvParty(friend.code)
                    },
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = Color.White.copy(alpha = 0.7f),
        modifier = Modifier.padding(start = NuvioTokens.Space.s8, top = NuvioTokens.Space.s12, bottom = NuvioTokens.Space.s2),
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = Color.White.copy(alpha = 0.5f),
        modifier = Modifier.padding(horizontal = NuvioTokens.Space.s8),
    )
}
