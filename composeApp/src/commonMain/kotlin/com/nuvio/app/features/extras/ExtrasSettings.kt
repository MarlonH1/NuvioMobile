package com.nuvio.app.features.extras

import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal val extrasJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** Player bar controls that can be reordered, moved behind "More" or hidden. */
enum class PlayerControlId {
    NextEpisode, Subtitles, Audio, Sources, Episodes, Speed, Resize,
    ExternalPlayer, StreamInfo, VideoSettings, SubmitIntro, Rate,
}

enum class PlayerControlPlacement { Bar, More, Hidden }

@Serializable
data class PlayerControlSlot(val id: String, val placement: String)

@Serializable
data class ExtrasSettings(
    // Empty until the user customizes it: the player then keeps its stock layout.
    val controlLayout: List<PlayerControlSlot> = emptyList(),
    val animeSpeedEnabled: Boolean = false,
    val animeSpeed: Float = DEFAULT_ANIME_SPEED,
    val episodeBlurDp: Int = DEFAULT_EPISODE_BLUR_DP,
    // Empty until customized: the TV remote screen then shows every button in stock order.
    val remoteButtons: List<RemoteButtonSlot> = emptyList(),
)

/** Buttons in the TV remote's now-playing row. */
enum class RemoteButtonId { Subtitles, Audio, Speed, Remote, Next, Exit }

@Serializable
data class RemoteButtonSlot(val id: String, val enabled: Boolean = true)

data class RemoteButtonEntry(val id: RemoteButtonId, val enabled: Boolean)

/** Saved order and on/off state; unknown ids are dropped and new buttons appended, enabled. */
fun normalizedRemoteButtons(saved: List<RemoteButtonSlot>): List<RemoteButtonEntry> {
    val entries = saved.mapNotNull { slot ->
        RemoteButtonId.entries.firstOrNull { it.name == slot.id }?.let { RemoteButtonEntry(it, slot.enabled) }
    }.distinctBy { it.id }
    return entries + RemoteButtonId.entries.filter { id -> entries.none { it.id == id } }.map { RemoteButtonEntry(it, true) }
}

const val DEFAULT_ANIME_SPEED = 1.5f
const val DEFAULT_EPISODE_BLUR_DP = 18
val ANIME_SPEED_OPTIONS = listOf(1.25f, 1.5f, 1.75f, 2f)
val EPISODE_BLUR_OPTIONS_DP = listOf(6, 12, 18, 28, 40)

/** Stock order; the first five sit in the bar, the rest behind "More". */
val DEFAULT_PLAYER_CONTROL_ORDER = listOf(
    PlayerControlId.NextEpisode, PlayerControlId.Subtitles, PlayerControlId.Audio,
    PlayerControlId.Sources, PlayerControlId.Episodes,
    PlayerControlId.Speed, PlayerControlId.Resize, PlayerControlId.ExternalPlayer,
    PlayerControlId.StreamInfo, PlayerControlId.VideoSettings, PlayerControlId.SubmitIntro,
    PlayerControlId.Rate,
)

data class PlayerControlEntry(val id: PlayerControlId, val placement: PlayerControlPlacement)

/** Saved layout with unknown ids dropped and new controls appended to "More". */
fun normalizedControlLayout(saved: List<PlayerControlSlot>): List<PlayerControlEntry> {
    val entries = saved.mapNotNull { slot ->
        val id = PlayerControlId.entries.firstOrNull { it.name == slot.id } ?: return@mapNotNull null
        val placement = PlayerControlPlacement.entries.firstOrNull { it.name == slot.placement }
            ?: PlayerControlPlacement.Bar
        PlayerControlEntry(id, placement)
    }.distinctBy { it.id }
    if (entries.isEmpty()) {
        return DEFAULT_PLAYER_CONTROL_ORDER.mapIndexed { index, id ->
            PlayerControlEntry(id, if (index < 5) PlayerControlPlacement.Bar else PlayerControlPlacement.More)
        }
    }
    val missing = DEFAULT_PLAYER_CONTROL_ORDER.filter { id -> entries.none { it.id == id } }
    return entries + missing.map { PlayerControlEntry(it, PlayerControlPlacement.More) }
}

/** Moves [id] to a 1-based [position] within [placement] (position is ignored for Hidden). */
fun moveControl(
    layout: List<PlayerControlEntry>,
    id: PlayerControlId,
    placement: PlayerControlPlacement,
    position: Int,
): List<PlayerControlEntry> {
    val rest = layout.filterNot { it.id == id }.toMutableList()
    val group = rest.filter { it.placement == placement }
    val index = (position - 1).coerceIn(0, group.size)
    val insertAt = when {
        index < group.size -> rest.indexOf(group[index])
        group.isNotEmpty() -> rest.indexOf(group.last()) + 1
        else -> rest.size
    }
    rest.add(insertAt, PlayerControlEntry(id, placement))
    return rest
}

object ExtrasSettingsRepository {
    private const val KEY = "extras_settings"
    private val _state = MutableStateFlow(ExtrasSettings())
    val state: StateFlow<ExtrasSettings> = _state.asStateFlow()

    /** Re-reads the active profile's settings; call when a screen that uses them opens. */
    fun reload(): ExtrasSettings {
        val loaded = ExtrasStorage.loadProfileValue(KEY)
            ?.let { runCatching { extrasJson.decodeFromString<ExtrasSettings>(it) }.getOrNull() }
            ?: ExtrasSettings()
        _state.value = loaded
        return loaded
    }

    fun update(transform: (ExtrasSettings) -> ExtrasSettings) {
        val next = transform(_state.value)
        _state.value = next
        ExtrasStorage.saveProfileValue(KEY, extrasJson.encodeToString(ExtrasSettings.serializer(), next))
    }

    fun setControlLayout(layout: List<PlayerControlEntry>) = update { settings ->
        settings.copy(controlLayout = layout.map { PlayerControlSlot(it.id.name, it.placement.name) })
    }

    fun resetControlLayout() = update { it.copy(controlLayout = emptyList()) }

    fun setRemoteButtons(buttons: List<RemoteButtonEntry>) = update { settings ->
        settings.copy(remoteButtons = buttons.map { RemoteButtonSlot(it.id.name, it.enabled) })
    }

    fun resetRemoteButtons() = update { it.copy(remoteButtons = emptyList()) }
}

private val ANIME_ID_PREFIXES = listOf("kitsu:", "mal:", "anilist:", "anidb:")

/** Same rules as the TV app: anime ids or type, an "Anime" genre, or Japanese animation. */
fun isAnimeContent(
    id: String?,
    type: String?,
    genres: List<String>,
    country: String?,
    language: String?,
): Boolean {
    val normalizedId = id.orEmpty().trim().lowercase()
    if (ANIME_ID_PREFIXES.any { normalizedId.startsWith(it) }) return true
    if (type.orEmpty().trim().equals("anime", ignoreCase = true)) return true
    val genreNames = genres.map { it.trim().lowercase() }
    if ("anime" in genreNames) return true
    if ("animation" !in genreNames) return false
    val lang = language.orEmpty().trim().lowercase()
    if (lang == "ja" || lang == "jpn" || lang == "japanese") return true
    return Regex("(^|[\\s,;])(jp|jpn|japan)($|[\\s,;])").containsMatchIn(country.orEmpty().lowercase())
}

const val PERSONAL_RATING_MAX = 10.0
const val PERSONAL_RATING_STEP = 0.5

fun normalizePersonalRating(value: Double): Double =
    ((value.coerceIn(0.0, PERSONAL_RATING_MAX) / PERSONAL_RATING_STEP).roundToInt() * PERSONAL_RATING_STEP)

fun formatPersonalRating(value: Double): String {
    val tenths = (normalizePersonalRating(value) * 10).roundToInt()
    return "${tenths / 10}.${tenths % 10}"
}

/** Same keys as the TV app: titles by meta id, episodes by video id. */
fun personalRatingKey(itemId: String?, videoId: String? = null, episode: Boolean = false): String? {
    val id = (if (episode) videoId else itemId)?.trim().orEmpty()
    if (id.isEmpty()) return null
    return if (episode) "episode:$id" else "title:$id"
}

@Serializable
data class PersonalRating(val rating: Double, val title: String = "", val updatedAt: Long = 0L)

object PersonalRatingsRepository {
    private const val KEY = "personal_ratings"
    private val _ratings = MutableStateFlow<Map<String, PersonalRating>>(emptyMap())
    val ratings: StateFlow<Map<String, PersonalRating>> = _ratings.asStateFlow()

    fun reload() {
        _ratings.value = ExtrasStorage.loadProfileValue(KEY)
            ?.let { runCatching { extrasJson.decodeFromString<Map<String, PersonalRating>>(it) }.getOrNull() }
            ?: emptyMap()
    }

    fun get(key: String?): Double? = key?.let { _ratings.value[it]?.rating }

    fun set(key: String, rating: Double?, title: String, nowMs: Long) {
        val next = _ratings.value.toMutableMap()
        if (rating == null) {
            next.remove(key)
        } else {
            next[key] = PersonalRating(normalizePersonalRating(rating), title.take(120), nowMs)
        }
        _ratings.value = next
        ExtrasStorage.saveProfileValue(KEY, extrasJson.encodeToString(next))
    }
}
