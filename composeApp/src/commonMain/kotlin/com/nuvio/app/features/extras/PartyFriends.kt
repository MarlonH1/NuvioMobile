package com.nuvio.app.features.extras

import co.touchlab.kermit.Logger
import com.nuvio.app.core.network.SupabaseProvider
import com.nuvio.app.core.network.createApiHttpClient
import com.nuvio.app.features.profiles.ProfileRepository
import io.github.jan.supabase.auth.auth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/*
 * Watch party friends, compatible with the TV app: every device listens on an
 * ntfy.sh topic derived from its own friend code, and an invite carries the
 * party code, title and sender's code. Nuvio's backend has no friends feature,
 * and these requests carry no Nuvio credentials.
 *
 * Signed-in accounts keep the code and friend list in the account's
 * user_metadata (key [ACCOUNT_METADATA_KEY], same format as the TV app), so
 * every device of one account shares one code.
 *
 * Friendship is mutual: adding a code sends a "friend-hello" request; the friend's
 * app adds you back and answers with its active profile name (invites carry the
 * name as well). Removing someone sends "friend-remove", which drops you there too.
 */

private const val NTFY_BASE_URL = "https://ntfy.sh"
private const val TOPIC_PREFIX = "nuvio-party-friend-"
const val FRIEND_CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
const val FRIEND_CODE_LENGTH = 12
private const val MAX_FRIENDS = 20
private const val MAX_NAME_LENGTH = 30
private const val INVITE_MAX_AGE_SECONDS = 600L
// ntfy.sh keeps messages about this long, so a name request reaches an offline friend later.
private const val HELLO_MAX_AGE_SECONDS = 12 * 60 * 60L
private const val POLL_INTERVAL_MS = 8_000L
private const val SEEN_LIMIT = 200
private const val DEFAULT_OWN_NAME = "Nuvio"
const val ACCOUNT_METADATA_KEY = "nuvio_friends"
private const val PARTY_CODE_PATTERN = "^[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{6}$"

@Serializable
data class PartyFriend(val code: String, val name: String = "")

@Serializable
internal data class PartyFriendsState(
    val code: String = "",
    val friends: List<PartyFriend> = emptyList(),
    val seen: List<String> = emptyList(),
    /** Account this device's code and list were last synced with; empty when never synced. */
    val accountUserId: String = "",
    /** Epoch millis of the last change to code or friends. */
    val updated: Long = 0,
)

/** Code and friend list as stored in the account's user_metadata. */
@Serializable
internal data class AccountFriends(
    val v: Int = 1,
    val code: String = "",
    val friends: List<PartyFriend> = emptyList(),
    val updated: Long = 0,
)

internal data class AccountMerge(val state: PartyFriendsState, val push: Boolean)

/**
 * Reconciles this device with the account copy. A device syncing an account for the
 * first time keeps its own friends (merged in); afterwards the newer copy wins, so
 * removals carry over. Switching to another account drops the old account's list.
 */
internal fun mergeAccountFriends(
    local: PartyFriendsState,
    remote: AccountFriends?,
    userId: String,
    now: Long,
    newCode: () -> String,
): AccountMerge {
    val account = remote?.takeIf { isValidFriendCode(it.code) }?.let { valid ->
        valid.copy(friends = valid.friends.filter { isValidFriendCode(it.code) && it.code != valid.code }.take(MAX_FRIENDS))
    }
    val linked = local.copy(accountUserId = userId)
    return when {
        local.accountUserId == userId -> when {
            account == null -> AccountMerge(linked.copy(code = local.code.ifEmpty { newCode() }), push = true)
            account.updated > local.updated ->
                AccountMerge(linked.copy(code = account.code, friends = account.friends, updated = account.updated), push = false)
            account.updated < local.updated || account.code != local.code -> AccountMerge(linked, push = true)
            else -> AccountMerge(linked, push = false)
        }
        local.accountUserId.isEmpty() -> if (account == null) {
            AccountMerge(linked.copy(code = local.code.ifEmpty { newCode() }, updated = now), push = true)
        } else {
            val extra = local.friends.filter { friend ->
                friend.code != account.code && account.friends.none { it.code == friend.code }
            }
            val friends = (account.friends + extra).take(MAX_FRIENDS)
            AccountMerge(
                linked.copy(code = account.code, friends = friends, updated = if (extra.isEmpty()) account.updated else now),
                push = extra.isNotEmpty(),
            )
        }
        else -> if (account == null) {
            AccountMerge(linked.copy(code = newCode(), friends = emptyList(), updated = now), push = true)
        } else {
            AccountMerge(linked.copy(code = account.code, friends = account.friends, updated = account.updated), push = false)
        }
    }
}

@OptIn(ExperimentalUuidApi::class)
private fun randomFriendCode(): String =
    // Uuid.random() draws from a secure RNG; 32 symbols divide 256 evenly.
    Uuid.random().toByteArray().take(FRIEND_CODE_LENGTH)
        .joinToString("") { FRIEND_CODE_ALPHABET[(it.toInt() and 0xff) % FRIEND_CODE_ALPHABET.length].toString() }

@Serializable
private data class InviteSender(val name: String = "", val code: String = "")

@Serializable
private data class InvitePayload(
    val v: Int = 1,
    val type: String = "",
    /** friend-hello only: true asks the receiver to answer with its name. */
    val request: Boolean = false,
    val party: String = "",
    val title: String = "",
    val from: InviteSender = InviteSender(),
)

data class PartyInvite(
    val partyCode: String,
    val title: String,
    val senderName: String,
    val knownFriend: Boolean,
)

enum class AddFriendError { InvalidCode, OwnCode, TooMany }

/** The friend's own name, or the formatted code until their app has answered. */
val PartyFriend.displayName: String get() = name.ifEmpty { formatFriendCode(code) }

fun normalizeFriendCode(input: String): String = input.uppercase().filter { it in FRIEND_CODE_ALPHABET }

fun isValidFriendCode(code: String): Boolean =
    code.length == FRIEND_CODE_LENGTH && normalizeFriendCode(code) == code

fun formatFriendCode(code: String): String = code.chunked(4).joinToString("-")

private fun cleanName(value: String, limit: Int = MAX_NAME_LENGTH): String =
    value.filter { it.code > 0x1f && it.code != 0x7f }.trim().take(limit)

private fun strictPartyCode(value: String): String? =
    value.uppercase().takeIf { Regex(PARTY_CODE_PATTERN).matches(it) }

object PartyFriendsRepository {
    private const val KEY = "party_friends"
    private val log = Logger.withTag("PartyFriends")
    private val http by lazy { createApiHttpClient() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val syncMutex = Mutex()
    private var signedInUserId: String? = null
    private val _friends = MutableStateFlow<List<PartyFriend>>(emptyList())
    val friends: StateFlow<List<PartyFriend>> = _friends.asStateFlow()
    private val _ownCode = MutableStateFlow("")
    val ownCodeState: StateFlow<String> = _ownCode.asStateFlow()
    private val _incoming = MutableStateFlow<PartyInvite?>(null)
    /** Latest invite not yet shown in the app; cleared by [consumeIncoming]. */
    val incoming: StateFlow<PartyInvite?> = _incoming.asStateFlow()

    private fun read(): PartyFriendsState =
        ExtrasStorage.loadDeviceValue(KEY)
            ?.let { runCatching { extrasJson.decodeFromString<PartyFriendsState>(it) }.getOrNull() }
            ?: PartyFriendsState()

    private fun write(state: PartyFriendsState) {
        ExtrasStorage.saveDeviceValue(KEY, extrasJson.encodeToString(state))
        _friends.value = state.friends
        _ownCode.value = state.code
    }

    fun reload() {
        _friends.value = read().friends
        _ownCode.value = ownCode()
    }

    fun ownCode(): String {
        val state = read()
        if (isValidFriendCode(state.code)) return state.code
        val code = randomFriendCode()
        write(state.copy(code = code, updated = nowMillis()))
        return code
    }

    /**
     * Pulls the account copy and reconciles it with this device; pass null when
     * signed out or anonymous (local changes then stay on this device).
     */
    suspend fun syncWithAccount(userId: String?) {
        signedInUserId = userId
        if (userId.isNullOrBlank()) return
        syncMutex.withLock {
            runCatching {
                val user = SupabaseProvider.client.auth.retrieveUserForCurrentSession(updateSession = true)
                if (user.id != userId) return@withLock
                // A copy we can't decode must not be treated as missing, or the merge would overwrite it.
                val remote = user.userMetadata?.get(ACCOUNT_METADATA_KEY)
                    ?.let { extrasJson.decodeFromJsonElement(AccountFriends.serializer(), it) }
                val merge = mergeAccountFriends(read(), remote, userId, nowMillis(), ::randomFriendCode)
                write(merge.state)
                if (merge.push) pushLocked(merge.state)
            }.onFailure { log.w(it) { "Friend list sync failed" } }
        }
    }

    private suspend fun pushLocked(state: PartyFriendsState) {
        val payload = extrasJson.encodeToJsonElement(
            AccountFriends.serializer(),
            AccountFriends(code = state.code, friends = state.friends, updated = state.updated),
        )
        // GoTrue merges top-level keys, so other metadata stays untouched.
        SupabaseProvider.client.auth.updateUser { data = buildJsonObject { put(ACCOUNT_METADATA_KEY, payload) } }
    }

    private fun pushChange() {
        val userId = signedInUserId ?: return
        scope.launch {
            syncMutex.withLock {
                val state = read()
                if (state.accountUserId != userId) return@withLock
                runCatching { pushLocked(state) }.onFailure { log.w(it) { "Friend list upload failed" } }
            }
        }
    }

    private fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

    /** Adds a friend by code; their name arrives once their app answers the hello. */
    fun add(rawCode: String): AddFriendError? {
        val code = normalizeFriendCode(rawCode)
        if (!isValidFriendCode(code)) return AddFriendError.InvalidCode
        if (code == ownCode()) return AddFriendError.OwnCode
        val state = read()
        if (state.friends.none { it.code == code }) {
            if (state.friends.size >= MAX_FRIENDS) return AddFriendError.TooMany
            write(state.copy(friends = state.friends + PartyFriend(code, ""), updated = nowMillis()))
            pushChange()
        }
        scope.launch { sendHello(code, request = true) }
        return null
    }

    /** Asks friends whose name is still unknown to send it (once per app start). */
    private suspend fun requestMissingNames() {
        read().friends.filter { it.name.isEmpty() }.forEach { sendHello(it.code, request = true) }
    }

    private fun ownName(): String =
        cleanName(ProfileRepository.state.value.activeProfile?.name.orEmpty()).ifEmpty { DEFAULT_OWN_NAME }

    private suspend fun sendHello(toCode: String, request: Boolean): Boolean =
        post(toCode, InvitePayload(type = "friend-hello", request = request, from = InviteSender(ownName(), ownCode())))

    private suspend fun post(toCode: String, payload: InvitePayload): Boolean {
        if (!isValidFriendCode(toCode)) return false
        return runCatching {
            http.post("$NTFY_BASE_URL/$TOPIC_PREFIX$toCode") {
                contentType(ContentType.Text.Plain)
                setBody(extrasJson.encodeToString(payload))
            }.status.isSuccess()
        }.getOrDefault(false)
    }

    /** Stores the name a friend sent about themselves. */
    private fun learnName(code: String, rawName: String) {
        val name = cleanName(rawName)
        if (name.isEmpty()) return
        val state = read()
        if (state.friends.none { it.code == code && it.name != name }) return
        write(state.copy(friends = state.friends.map { if (it.code == code) it.copy(name = name) else it }, updated = nowMillis()))
        pushChange()
    }

    /** Removes the friend here and asks their app to drop us as well. */
    fun remove(code: String) {
        if (!removeLocally(code)) return
        scope.launch {
            post(code, InvitePayload(type = "friend-remove", from = InviteSender(ownName(), ownCode())))
        }
    }

    /** The friend removed us: drop them here too (without telling them again). */
    private fun removeLocally(code: String): Boolean {
        val state = read()
        if (state.friends.none { it.code == code }) return false
        write(state.copy(friends = state.friends.filterNot { it.code == code }, updated = nowMillis()))
        pushChange()
        return true
    }

    /** Someone added our code: add them back, so the friendship goes both ways. */
    private fun addRequester(code: String, rawName: String) {
        val state = read()
        if (state.friends.any { it.code == code } || state.friends.size >= MAX_FRIENDS) return
        write(state.copy(friends = state.friends + PartyFriend(code, cleanName(rawName)), updated = nowMillis()))
        pushChange()
    }

    suspend fun sendInvite(friend: PartyFriend, partyCode: String, title: String, fromName: String): Boolean {
        val code = strictPartyCode(partyCode) ?: return false
        return post(
            friend.code,
            InvitePayload(
                type = "party-invite",
                party = code,
                title = cleanName(title, 120),
                from = InviteSender(cleanName(fromName).ifEmpty { ownName() }, ownCode()),
            ),
        )
    }

    fun consumeIncoming() {
        _incoming.value = null
    }

    /** Polls this device's topic while the app process runs; never returns. */
    suspend fun pollInvites(onInvite: suspend (PartyInvite) -> Unit) {
        requestMissingNames()
        while (true) {
            runCatching {
                // Re-read each round: an account sync can replace the code.
                val topic = TOPIC_PREFIX + ownCode()
                val text = http.get("$NTFY_BASE_URL/$topic/json?poll=1&since=${HELLO_MAX_AGE_SECONDS}s").bodyAsText()
                text.lineSequence().filter { it.isNotBlank() }.forEach { line ->
                    handleMessage(line)?.let { invite ->
                        _incoming.value = invite
                        onInvite(invite)
                    }
                }
            }
            delay(POLL_INTERVAL_MS)
        }
    }

    /** Handles one ntfy event: returns an invite to show, or null (hellos are handled here). */
    private suspend fun handleMessage(line: String): PartyInvite? {
        val event = runCatching { extrasJson.parseToJsonElement(line).jsonObject }.getOrNull() ?: return null
        if (event.string("event") != "message") return null
        val id = event.string("id") ?: return null
        val time = event["time"]?.jsonPrimitive?.longOrNull ?: return null
        val ageSeconds = Clock.System.now().toEpochMilliseconds() / 1000 - time
        if (ageSeconds > HELLO_MAX_AGE_SECONDS) return null
        val payload = event.string("message")
            ?.let { runCatching { extrasJson.decodeFromString<InvitePayload>(it) }.getOrNull() }
            ?: return null
        val state = read()
        if (id in state.seen) return null
        write(state.copy(seen = (state.seen + id).takeLast(SEEN_LIMIT)))
        val senderCode = normalizeFriendCode(payload.from.code)
            .takeIf { isValidFriendCode(it) && it != state.code }
        if (senderCode != null) learnName(senderCode, payload.from.name)
        if (payload.type == "friend-hello") {
            if (senderCode != null && payload.request) {
                addRequester(senderCode, payload.from.name)
                sendHello(senderCode, request = false)
            }
            return null
        }
        if (payload.type == "friend-remove") {
            if (senderCode != null) removeLocally(senderCode)
            return null
        }
        val partyCode = strictPartyCode(payload.party)
        if (payload.type != "party-invite" || partyCode == null || ageSeconds > INVITE_MAX_AGE_SECONDS) return null
        val friend = read().friends.firstOrNull { it.code == senderCode }
        return PartyInvite(
            partyCode = partyCode,
            title = cleanName(payload.title, 120),
            senderName = friend?.displayName ?: cleanName(payload.from.name),
            knownFriend = friend != null,
        )
    }

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
}

/** Shows an invite as a system notification that opens the party join flow. */
internal expect object PartyInviteNotifier {
    suspend fun show(invite: PartyInvite): Boolean
}
