package com.nuvio.app.features.extras

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.deeplink.handleAppUrl
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.features.settings.SettingsGroup
import com.nuvio.app.features.settings.SettingsGroupDivider
import com.nuvio.app.features.settings.SettingsNavigationRow
import com.nuvio.app.features.settings.SettingsSection
import com.nuvio.app.features.settings.SettingsSwitchRow
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

data class RatingRequest(val key: String, val title: String)

/** App-wide prompts, so sheets and the player can open them and close themselves. */
object ExtrasPrompts {
    private val _rating = MutableStateFlow<RatingRequest?>(null)
    val rating: StateFlow<RatingRequest?> = _rating.asStateFlow()

    fun requestRating(key: String?, title: String) {
        if (key != null) _rating.value = RatingRequest(key, title)
    }

    fun dismissRating() {
        _rating.value = null
    }

    /** What the player is showing, set by the player so its Rate button needs no wiring. */
    var currentPlayerTarget: RatingRequest? = null

    fun requestCurrentPlayerRating() {
        currentPlayerTarget?.let { _rating.value = it }
    }
}

/** Unwatched-episode blur radius chosen in settings. */
@Composable
fun rememberEpisodeBlurRadius(): Dp {
    val settings by ExtrasSettingsRepository.state.collectAsStateWithLifecycle()
    return settings.episodeBlurDp.dp
}

/** Place once at the app root: polls friend invites and hosts the extras dialogs. */
@Composable
fun ExtrasOverlayHost() {
    LaunchedEffect(Unit) {
        ExtrasSettingsRepository.reload()
        PersonalRatingsRepository.reload()
        PartyFriendsRepository.reload()
        PartyFriendsRepository.pollInvites { invite ->
            // A shown notification replaces the in-app prompt.
            if (PartyInviteNotifier.show(invite)) PartyFriendsRepository.consumeIncoming()
        }
    }
    val ratingRequest by ExtrasPrompts.rating.collectAsStateWithLifecycle()
    ratingRequest?.let { request ->
        PersonalRatingDialog(request = request, onDismiss = ExtrasPrompts::dismissRating)
    }
    val invite by PartyFriendsRepository.incoming.collectAsStateWithLifecycle()
    invite?.let { IncomingInviteDialog(it) }
}

@Composable
private fun IncomingInviteDialog(invite: PartyInvite) {
    val sender = invite.senderName.ifBlank { stringResource(Res.string.extras_invite_someone) }
    val title = invite.title.ifBlank { stringResource(Res.string.party_title) }
    AlertDialog(
        onDismissRequest = PartyFriendsRepository::consumeIncoming,
        title = { Text(stringResource(Res.string.extras_invite_title)) },
        text = {
            Text(
                stringResource(
                    if (invite.knownFriend) Res.string.extras_invite_message else Res.string.extras_invite_unknown,
                    sender,
                    title,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                PartyFriendsRepository.consumeIncoming()
                handleAppUrl("nuvio://party/${invite.partyCode}")
            }) { Text(stringResource(Res.string.extras_invite_join)) }
        },
        dismissButton = {
            TextButton(onClick = PartyFriendsRepository::consumeIncoming) {
                Text(stringResource(Res.string.extras_invite_dismiss))
            }
        },
    )
}

@Composable
private fun PersonalRatingDialog(request: RatingRequest, onDismiss: () -> Unit) {
    val ratings by PersonalRatingsRepository.ratings.collectAsStateWithLifecycle()
    val existing = ratings[request.key]?.rating
    var value by remember(request) { mutableStateOf(existing ?: 7.0) }
    val save = { rating: Double? ->
        PersonalRatingsRepository.set(request.key, rating, request.title, Clock.System.now().toEpochMilliseconds())
        onDismiss()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.extras_rating_title)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                if (request.title.isNotBlank()) {
                    Text(request.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                    Spacer(Modifier.padding(top = 8.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { value = normalizePersonalRating(value - PERSONAL_RATING_STEP) }) {
                        Icon(Icons.Rounded.Remove, contentDescription = "−$PERSONAL_RATING_STEP")
                    }
                    Text(
                        text = "★ ${formatPersonalRating(value)}",
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    IconButton(onClick = { value = normalizePersonalRating(value + PERSONAL_RATING_STEP) }) {
                        Icon(Icons.Rounded.Add, contentDescription = "+$PERSONAL_RATING_STEP")
                    }
                }
                Slider(
                    value = value.toFloat(),
                    onValueChange = { value = normalizePersonalRating(it.toDouble()) },
                    valueRange = 0f..PERSONAL_RATING_MAX.toFloat(),
                    // 0.5 steps between 0 and 10: 19 stops between the ends.
                    steps = (PERSONAL_RATING_MAX / PERSONAL_RATING_STEP).roundToInt() - 1,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { save(value) }) { Text(stringResource(Res.string.extras_save)) }
        },
        dismissButton = {
            Row {
                if (existing != null) {
                    TextButton(onClick = { save(null) }) { Text(stringResource(Res.string.extras_rating_remove)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.extras_cancel)) }
            }
        },
    )
}

@Composable
fun AddFriendDialog(onDismiss: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<AddFriendError?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.extras_friends_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase().take(16); error = null },
                    label = { Text(stringResource(Res.string.extras_friends_code_label)) },
                    placeholder = { Text("XXXX-XXXX-XXXX") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(30); error = null },
                    label = { Text(stringResource(Res.string.extras_friends_name_label)) },
                    singleLine = true,
                )
                error?.let {
                    Text(
                        stringResource(
                            when (it) {
                                AddFriendError.InvalidCode -> Res.string.extras_friends_error_code
                                AddFriendError.OwnCode -> Res.string.extras_friends_error_own
                                AddFriendError.MissingName -> Res.string.extras_friends_error_name
                                AddFriendError.TooMany -> Res.string.extras_friends_error_full
                            },
                        ),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = PartyFriendsRepository.add(code, name)
                if (error == null) onDismiss()
            }) { Text(stringResource(Res.string.extras_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.extras_cancel)) } },
    )
}

/** Friend invite buttons for the watch party panel (party screen and player). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun FriendInviteSection(partyCode: String, title: String, fromName: String) {
    val friends by PartyFriendsRepository.friends.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<PartyFriend?>(null) }
    var result by remember { mutableStateOf<Boolean?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(Res.string.extras_friends_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (friends.isEmpty()) {
            Text(stringResource(Res.string.extras_friends_none), style = MaterialTheme.typography.bodyMedium)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                friends.forEach { friend ->
                    AssistChip(
                        onClick = {
                            pending = friend
                            result = null
                            scope.launch {
                                result = PartyFriendsRepository.sendInvite(friend, partyCode, title, fromName)
                            }
                        },
                        label = { Text(stringResource(Res.string.extras_friends_invite, friend.name)) },
                        leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                    )
                }
            }
        }
        val friend = pending
        val sent = result
        if (friend != null && sent != null) {
            Text(
                stringResource(if (sent) Res.string.extras_friends_sent else Res.string.extras_friends_failed, friend.name),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private fun controlLabel(id: PlayerControlId): StringResource = when (id) {
    PlayerControlId.NextEpisode -> Res.string.extras_control_next_episode
    PlayerControlId.Subtitles -> Res.string.extras_control_subtitles
    PlayerControlId.Audio -> Res.string.extras_control_audio
    PlayerControlId.Sources -> Res.string.extras_control_sources
    PlayerControlId.Episodes -> Res.string.extras_control_episodes
    PlayerControlId.Speed -> Res.string.extras_control_speed
    PlayerControlId.Resize -> Res.string.extras_control_resize
    PlayerControlId.ExternalPlayer -> Res.string.extras_control_external
    PlayerControlId.StreamInfo -> Res.string.extras_control_stream_info
    PlayerControlId.VideoSettings -> Res.string.extras_control_video_settings
    PlayerControlId.SubmitIntro -> Res.string.extras_control_submit_intro
    PlayerControlId.Rate -> Res.string.extras_control_rate
}

private fun placementLabel(placement: PlayerControlPlacement): StringResource = when (placement) {
    PlayerControlPlacement.Bar -> Res.string.extras_placement_bar
    PlayerControlPlacement.More -> Res.string.extras_placement_more
    PlayerControlPlacement.Hidden -> Res.string.extras_placement_hidden
}

@Composable
private fun PlayerControlsDialog(onDismiss: () -> Unit) {
    val settings by ExtrasSettingsRepository.state.collectAsStateWithLifecycle()
    val layout = normalizedControlLayout(settings.controlLayout)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.extras_player_controls_title)) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 480.dp)) {
                itemsIndexed(layout, key = { _, entry -> entry.id.name }) { index, entry ->
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(controlLabel(entry.id)), modifier = Modifier.weight(1f))
                            IconButton(
                                enabled = index > 0,
                                onClick = {
                                    val list = layout.toMutableList()
                                    list.add(index - 1, list.removeAt(index))
                                    ExtrasSettingsRepository.setControlLayout(list)
                                },
                            ) { Icon(Icons.Rounded.KeyboardArrowUp, stringResource(Res.string.extras_move_up)) }
                            IconButton(
                                enabled = index < layout.lastIndex,
                                onClick = {
                                    val list = layout.toMutableList()
                                    list.add(index + 1, list.removeAt(index))
                                    ExtrasSettingsRepository.setControlLayout(list)
                                },
                            ) { Icon(Icons.Rounded.KeyboardArrowDown, stringResource(Res.string.extras_move_down)) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PlayerControlPlacement.entries.forEach { placement ->
                                FilterChip(
                                    selected = entry.placement == placement,
                                    onClick = {
                                        ExtrasSettingsRepository.setControlLayout(
                                            layout.map { if (it.id == entry.id) it.copy(placement = placement) else it },
                                        )
                                    },
                                    label = { Text(stringResource(placementLabel(placement))) },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.extras_save)) } },
        dismissButton = {
            TextButton(onClick = ExtrasSettingsRepository::resetControlLayout) {
                Text(stringResource(Res.string.extras_player_controls_reset))
            }
        },
    )
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(option)
                                onDismiss()
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            label(option),
                            fontWeight = if (option == selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (option == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.extras_cancel)) } },
    )
}

private fun blurLabel(dp: Int): StringResource = when (dp) {
    EPISODE_BLUR_OPTIONS_DP[0] -> Res.string.extras_blur_subtle
    EPISODE_BLUR_OPTIONS_DP[1] -> Res.string.extras_blur_light
    EPISODE_BLUR_OPTIONS_DP[2] -> Res.string.extras_blur_medium
    EPISODE_BLUR_OPTIONS_DP[3] -> Res.string.extras_blur_strong
    else -> Res.string.extras_blur_max
}

private fun formatSpeed(speed: Float): String {
    val hundredths = (speed * 100).roundToInt()
    return if (hundredths % 100 == 0) "${hundredths / 100}x" else "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0').trimEnd('0')}x"
}

/** Extras block for the playback settings page. */
internal fun LazyListScope.extrasSettingsSection(isTablet: Boolean) {
    item(key = "extras_settings") {
        remember {
            ExtrasSettingsRepository.reload()
            PartyFriendsRepository.reload()
        }
        val settings by ExtrasSettingsRepository.state.collectAsStateWithLifecycle()
        val friends by PartyFriendsRepository.friends.collectAsStateWithLifecycle()
        val clipboard = LocalClipboardManager.current
        val ownCode = remember { PartyFriendsRepository.ownCode() }
        val copiedLabel = stringResource(Res.string.extras_friends_copied)
        var showControls by remember { mutableStateOf(false) }
        var showAnimeSpeed by remember { mutableStateOf(false) }
        var showBlur by remember { mutableStateOf(false) }
        var showAddFriend by remember { mutableStateOf(false) }
        var friendToRemove by remember { mutableStateOf<PartyFriend?>(null) }

        SettingsSection(title = stringResource(Res.string.extras_section_title), isTablet = isTablet) {
            SettingsGroup(isTablet = isTablet) {
                SettingsNavigationRow(
                    title = stringResource(Res.string.extras_player_controls_title),
                    description = stringResource(Res.string.extras_player_controls_desc),
                    icon = Icons.Rounded.Tune,
                    isTablet = isTablet,
                    onClick = { showControls = true },
                )
                SettingsGroupDivider(isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.extras_anime_speed_title),
                    description = stringResource(Res.string.extras_anime_speed_desc),
                    checked = settings.animeSpeedEnabled,
                    isTablet = isTablet,
                    onCheckedChange = { enabled -> ExtrasSettingsRepository.update { it.copy(animeSpeedEnabled = enabled) } },
                )
                if (settings.animeSpeedEnabled) {
                    SettingsGroupDivider(isTablet)
                    SettingsNavigationRow(
                        title = stringResource(Res.string.extras_anime_speed_value),
                        description = formatSpeed(settings.animeSpeed),
                        icon = Icons.Rounded.Speed,
                        isTablet = isTablet,
                        onClick = { showAnimeSpeed = true },
                    )
                }
                SettingsGroupDivider(isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.extras_blur_strength_title),
                    description = "${stringResource(blurLabel(settings.episodeBlurDp))} · ${stringResource(Res.string.extras_blur_strength_desc)}",
                    icon = Icons.Rounded.BlurOn,
                    isTablet = isTablet,
                    onClick = { showBlur = true },
                )
            }
        }
        SettingsSection(title = stringResource(Res.string.extras_friends_title), isTablet = isTablet) {
            SettingsGroup(isTablet = isTablet) {
                SettingsNavigationRow(
                    title = stringResource(Res.string.extras_friends_own_code),
                    description = stringResource(Res.string.extras_friends_own_code_desc, formatFriendCode(ownCode)),
                    icon = Icons.Rounded.Badge,
                    isTablet = isTablet,
                    onClick = {
                        clipboard.setText(AnnotatedString(formatFriendCode(ownCode)))
                        NuvioToastController.show(copiedLabel)
                    },
                )
                friends.forEach { friend ->
                    SettingsGroupDivider(isTablet)
                    SettingsNavigationRow(
                        title = friend.name,
                        description = formatFriendCode(friend.code),
                        icon = Icons.Rounded.Person,
                        isTablet = isTablet,
                        onClick = { friendToRemove = friend },
                    )
                }
                SettingsGroupDivider(isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.extras_friends_add),
                    description = stringResource(Res.string.extras_friends_add_desc),
                    icon = Icons.Rounded.Add,
                    isTablet = isTablet,
                    onClick = { showAddFriend = true },
                )
            }
        }

        if (showControls) PlayerControlsDialog(onDismiss = { showControls = false })
        if (showAnimeSpeed) {
            ChoiceDialog(
                title = stringResource(Res.string.extras_anime_speed_value),
                options = ANIME_SPEED_OPTIONS,
                selected = settings.animeSpeed,
                label = { formatSpeed(it) },
                onSelect = { speed -> ExtrasSettingsRepository.update { it.copy(animeSpeed = speed) } },
                onDismiss = { showAnimeSpeed = false },
            )
        }
        if (showBlur) {
            ChoiceDialog(
                title = stringResource(Res.string.extras_blur_strength_title),
                options = EPISODE_BLUR_OPTIONS_DP,
                selected = settings.episodeBlurDp,
                label = { stringResource(blurLabel(it)) },
                onSelect = { dp -> ExtrasSettingsRepository.update { it.copy(episodeBlurDp = dp) } },
                onDismiss = { showBlur = false },
            )
        }
        if (showAddFriend) AddFriendDialog(onDismiss = { showAddFriend = false })
        friendToRemove?.let { friend ->
            AlertDialog(
                onDismissRequest = { friendToRemove = null },
                title = { Text(stringResource(Res.string.extras_friends_remove_title, friend.name)) },
                confirmButton = {
                    TextButton(onClick = {
                        PartyFriendsRepository.remove(friend.code)
                        friendToRemove = null
                    }) { Text(stringResource(Res.string.extras_friends_remove)) }
                },
                dismissButton = {
                    TextButton(onClick = { friendToRemove = null }) { Text(stringResource(Res.string.extras_cancel)) }
                },
            )
        }
    }
}

/** Small "★ 8.5" label for a personal rating, or nothing when unrated. */
@Composable
fun PersonalRatingBadge(key: String?, modifier: Modifier = Modifier) {
    val ratings by PersonalRatingsRepository.ratings.collectAsStateWithLifecycle()
    val rating = key?.let { ratings[it]?.rating } ?: return
    Text(
        text = "★ ${formatPersonalRating(rating)}",
        color = androidx.compose.ui.graphics.Color(0xFFF5C518),
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier,
    )
}
