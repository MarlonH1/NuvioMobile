package com.nuvio.app.features.extras

import com.nuvio.app.core.network.createApiHttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlin.random.Random
import kotlin.time.Clock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/*
 * Watch party friends, compatible with the TV app: every device listens on an
 * ntfy.sh topic derived from its own friend code, and an invite carries the
 * party code, title and sender's code. Nuvio's backend has no friends feature,
 * and these requests carry no Nuvio credentials.
 */

private const val NTFY_BASE_URL = "https://ntfy.sh"
private const val TOPIC_PREFIX = "nuvio-party-friend-"
const val FRIEND_CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
const val FRIEND_CODE_LENGTH = 12
private const val MAX_FRIENDS = 20
private const val MAX_NAME_LENGTH = 30
private const val INVITE_MAX_AGE_SECONDS = 600L
private const val POLL_INTERVAL_MS = 8_000L
private const val SEEN_LIMIT = 50
private const val PARTY_CODE_PATTERN = "^[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{6}$"

@Serializable
data class PartyFriend(val code: String, val name: String)

@Serializable
private data class PartyFriendsState(
    val code: String = "",
    val friends: List<PartyFriend> = emptyList(),
    val seen: List<String> = emptyList(),
)

@Serializable
private data class InviteSender(val name: String = "", val code: String = "")

@Serializable
private data class InvitePayload(
    val v: Int = 1,
    val type: String = "",
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

enum class AddFriendError { InvalidCode, OwnCode, MissingName, TooMany }

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
    private val http by lazy { createApiHttpClient() }
    private val _friends = MutableStateFlow<List<PartyFriend>>(emptyList())
    val friends: StateFlow<List<PartyFriend>> = _friends.asStateFlow()
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
    }

    fun reload() {
        _friends.value = read().friends
    }

    fun ownCode(): String {
        val state = read()
        if (isValidFriendCode(state.code)) return state.code
        val code = (1..FRIEND_CODE_LENGTH)
            .map { FRIEND_CODE_ALPHABET[Random.nextInt(FRIEND_CODE_ALPHABET.length)] }
            .joinToString("")
        write(state.copy(code = code))
        return code
    }

    fun add(rawCode: String, rawName: String): AddFriendError? {
        val code = normalizeFriendCode(rawCode)
        val name = cleanName(rawName)
        if (!isValidFriendCode(code)) return AddFriendError.InvalidCode
        if (code == ownCode()) return AddFriendError.OwnCode
        if (name.isEmpty()) return AddFriendError.MissingName
        val state = read()
        val existing = state.friends.any { it.code == code }
        if (!existing && state.friends.size >= MAX_FRIENDS) return AddFriendError.TooMany
        val friends = if (existing) {
            state.friends.map { if (it.code == code) it.copy(name = name) else it }
        } else {
            state.friends + PartyFriend(code, name)
        }
        write(state.copy(friends = friends))
        return null
    }

    fun remove(code: String) {
        val state = read()
        write(state.copy(friends = state.friends.filterNot { it.code == code }))
    }

    suspend fun sendInvite(friend: PartyFriend, partyCode: String, title: String, fromName: String): Boolean {
        val code = strictPartyCode(partyCode) ?: return false
        if (!isValidFriendCode(friend.code)) return false
        val body = extrasJson.encodeToString(
            InvitePayload(
                type = "party-invite",
                party = code,
                title = cleanName(title, 120),
                from = InviteSender(cleanName(fromName), ownCode()),
            ),
        )
        return runCatching {
            http.post("$NTFY_BASE_URL/$TOPIC_PREFIX${friend.code}") {
                contentType(ContentType.Text.Plain)
                setBody(body)
            }.status.isSuccess()
        }.getOrDefault(false)
    }

    fun consumeIncoming() {
        _incoming.value = null
    }

    /** Polls this device's topic while the app process runs; never returns. */
    suspend fun pollInvites(onInvite: suspend (PartyInvite) -> Unit) {
        val topic = TOPIC_PREFIX + ownCode()
        while (true) {
            runCatching {
                val text = http.get("$NTFY_BASE_URL/$topic/json?poll=1&since=${INVITE_MAX_AGE_SECONDS}s").bodyAsText()
                text.lineSequence().filter { it.isNotBlank() }.forEach { line ->
                    parseInvite(line)?.let { invite ->
                        _incoming.value = invite
                        onInvite(invite)
                    }
                }
            }
            delay(POLL_INTERVAL_MS)
        }
    }

    private fun parseInvite(line: String): PartyInvite? {
        val event = runCatching { extrasJson.parseToJsonElement(line).jsonObject }.getOrNull() ?: return null
        if (event.string("event") != "message") return null
        val id = event.string("id") ?: return null
        val time = event["time"]?.jsonPrimitive?.longOrNull ?: return null
        val nowSeconds = Clock.System.now().toEpochMilliseconds() / 1000
        if (nowSeconds - time > INVITE_MAX_AGE_SECONDS) return null
        val payload = event.string("message")
            ?.let { runCatching { extrasJson.decodeFromString<InvitePayload>(it) }.getOrNull() }
            ?: return null
        val partyCode = strictPartyCode(payload.party)
        if (payload.type != "party-invite" || partyCode == null) return null
        val state = read()
        if (id in state.seen) return null
        write(state.copy(seen = (state.seen + id).takeLast(SEEN_LIMIT)))
        val senderCode = normalizeFriendCode(payload.from.code)
        // Known friends show the name saved here, not the one the sender chose.
        val friend = state.friends.firstOrNull { it.code == senderCode }
        return PartyInvite(
            partyCode = partyCode,
            title = cleanName(payload.title, 120),
            senderName = friend?.name ?: cleanName(payload.from.name),
            knownFriend = friend != null,
        )
    }

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
}

/** Shows an invite as a system notification that opens the party join flow. */
internal expect object PartyInviteNotifier {
    suspend fun show(invite: PartyInvite): Boolean
}
