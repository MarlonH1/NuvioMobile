package com.nuvio.app.features.anime

import co.touchlab.kermit.Logger
import com.nuvio.app.features.addons.httpGetTextWithHeaders
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.player.skip.SimklIdResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

enum class FillerStatus { FILLER, MIXED }

internal data class AnimeFillerEpisodes(
    val fillers: Set<Int> = emptySet(),
    val mixed: Set<Int> = emptySet(),
) {
    val isEmpty: Boolean get() = fillers.isEmpty() && mixed.isEmpty()
}

/**
 * Filler / mixed-canon episode lookup (AnimeFillerList, with Jikan as fallback), ported from
 * NuvioCodeineXO. Results are published as a side table keyed by episode video id, so the
 * metadata model stays untouched.
 */
object AnimeFillerRepository {
    private val log = Logger.withTag("AnimeFillerRepo")
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val mutex = Mutex()

    private val _statusByVideoId = MutableStateFlow<Map<String, FillerStatus>>(emptyMap())
    val statusByVideoId: StateFlow<Map<String, FillerStatus>> = _statusByVideoId.asStateFlow()

    // Same data keyed by show + season + episode, so places that only know those (Continue
    // Watching, the player's pause screen) can show the marker too. Persisted per show.
    private val _statusByEpisode = MutableStateFlow<Map<String, FillerStatus>>(emptyMap())
    val statusByEpisode: StateFlow<Map<String, FillerStatus>> = _statusByEpisode.asStateFlow()
    private val loadedShowIds = mutableSetOf<String>()
    private const val SHOW_PREFIX = "anime_filler_show_v1:"

    fun episodeKey(metaId: String, season: Int, episode: Int): String = "$metaId|$season|$episode"

    /** Marker for an episode, by video id when known, else by show + season + episode. */
    fun statusFor(metaId: String?, season: Int?, episode: Int?, videoId: String? = null): FillerStatus? {
        videoId?.let { id -> _statusByVideoId.value[id]?.let { return it } }
        if (metaId.isNullOrBlank() || season == null || episode == null) return null
        return _statusByEpisode.value[episodeKey(metaId, season, episode)]
    }

    /** Loads the saved markers of a show (from an earlier visit) into memory, once. */
    fun ensureShowLoaded(metaId: String?) {
        if (metaId.isNullOrBlank() || !loadedShowIds.add(metaId)) return
        val payload = AnimeModeStorage.loadString(SHOW_PREFIX + metaId) ?: return
        val entries = payload.split(';').mapNotNull { entry ->
            val (episodePart, statusPart) = entry.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
            val (season, episode) = episodePart.split(':').mapNotNull { it.toIntOrNull() }
                .takeIf { it.size == 2 } ?: return@mapNotNull null
            val status = if (statusPart == "M") FillerStatus.MIXED else FillerStatus.FILLER
            episodeKey(metaId, season, episode) to status
        }.toMap()
        if (entries.isNotEmpty()) _statusByEpisode.update { it + entries }
    }

    private val memoryCache = mutableMapOf<String, AnimeFillerEpisodes>()
    private val resolvedMetaIds = mutableSetOf<String>()
    private var aflShowsCache: Map<String, String>? = null

    private const val AFL_BASE_URL = "https://www.animefillerlist.com"
    private const val JIKAN_BASE_URL = "https://api.jikan.moe/v4"
    private const val HTTP_TIMEOUT_MS = 6_000L
    private const val CACHE_PREFIX = "anime_filler_cache_v2:"

    private val REQUEST_HEADERS = mapOf(
        "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    )

    private val CONDENSED_DIV_REGEX = Regex("""(?s)<div class="([^"]*(?:filler|mixed)[^"]*)"[^>]*>.*?<span class="Episodes">(.*?)</span>""")
    private val EPISODE_RANGE_REGEX = Regex("""(\d+)(?:\s*-\s*(\d+))?""")
    private val ROW_REGEX = Regex("""<tr class="([^"]+)"[^>]*>\s*<td class="Number">(\d+)</td>""")
    private val SHOW_LINK_REGEX = Regex("""<a href="/shows/([^"]+)">([^<]+)</a>""")
    private val TAG_REGEX = Regex("""<[^>]+>""")

    /** [videos] without pure filler episodes when "Hide Filler Episodes" is on. */
    fun withoutHiddenFillers(videos: List<com.nuvio.app.features.details.MetaVideo>): List<com.nuvio.app.features.details.MetaVideo> {
        if (!AnimeModeRepository.snapshot().hidingFillers) return videos
        val statuses = _statusByVideoId.value
        return videos.filterNot { statuses[it.id] == FillerStatus.FILLER }
    }

    /** Looks up filler data for [meta] (anime series only) and publishes it. Safe to call repeatedly. */
    suspend fun resolve(meta: MetaDetails) {
        if (meta.videos.isEmpty()) return
        val knownAnime = AnimeContent.isAnime(meta)
        // Animated shows without anime-specific metadata are confirmed through Simkl below.
        if (!knownAnime && meta.genres.none { it.equals("animation", ignoreCase = true) }) return
        mutex.withLock {
            if (meta.id in resolvedMetaIds) return
            resolvedMetaIds += meta.id
        }
        val statuses = runCatching { computeStatuses(meta, knownAnime) }
            .onFailure { log.d { "Filler lookup failed for ${meta.name}: ${it.message}" } }
            .getOrNull()
            .orEmpty()
        if (statuses.isNotEmpty()) {
            _statusByVideoId.update { it + statuses }
            val byEpisode = meta.videos.mapNotNull { video ->
                val status = statuses[video.id] ?: return@mapNotNull null
                val season = video.season ?: return@mapNotNull null
                val episode = video.episode ?: return@mapNotNull null
                Triple(season, episode, status)
            }
            _statusByEpisode.update { current ->
                current + byEpisode.associate { (season, episode, status) -> episodeKey(meta.id, season, episode) to status }
            }
            AnimeModeStorage.saveString(
                SHOW_PREFIX + meta.id,
                byEpisode.joinToString(";") { (season, episode, status) ->
                    "$season:$episode=${if (status == FillerStatus.MIXED) "M" else "F"}"
                },
            )
            loadedShowIds += meta.id
        }
    }

    private suspend fun computeStatuses(meta: MetaDetails, knownAnime: Boolean): Map<String, FillerStatus> =
        withContext(Dispatchers.Default) {
            val (source, rawId) = parseIdSource(meta.id)
                ?: meta.imdbId?.takeIf { it.isNotBlank() }?.let { "imdb" to it }
                ?: ("" to "")
            val resolved = if (source.isNotBlank()) {
                withTimeoutOrNull(HTTP_TIMEOUT_MS) { SimklIdResolver.resolveIds(source, rawId, "anime") }
            } else {
                null
            }

            if (!knownAnime && resolved?.type != "anime") return@withContext emptyMap()

            val cacheKey = when {
                resolved != null && resolved.simklId > 0 -> "simkl:${resolved.simklId}"
                !resolved?.mal.isNullOrBlank() -> "mal:${resolved?.mal}"
                else -> "id:${meta.id}"
            }
            val episodes = cached(cacheKey) ?: fetchFillerEpisodes(meta, resolved?.mal).also { store(cacheKey, it) }
            if (episodes.isEmpty) return@withContext emptyMap()

            // Map TVDB season/episode numbering onto absolute anime episode numbers when Simkl knows it.
            val mapping = if (resolved != null && resolved.simklId > 0) {
                withTimeoutOrNull(HTTP_TIMEOUT_MS) {
                    SimklIdResolver.getEpisodeMapping(resolved.simklId, resolved.type)
                }.orEmpty()
            } else {
                emptyList()
            }

            buildMap {
                meta.videos.forEach { video ->
                    val absolute = if (mapping.isNotEmpty() && video.season != null && video.episode != null) {
                        mapping.firstOrNull { it.tvdbSeason == video.season && it.tvdbEpisode == video.episode }
                            ?.animeEpisode ?: video.episode
                    } else {
                        video.episode
                    } ?: return@forEach
                    when (absolute) {
                        in episodes.mixed -> put(video.id, FillerStatus.MIXED)
                        in episodes.fillers -> put(video.id, FillerStatus.FILLER)
                    }
                }
            }.also { log.d { "Tagged ${it.size} filler/mixed episodes for ${meta.name}" } }
        }

    private suspend fun fetchFillerEpisodes(meta: MetaDetails, malId: String?): AnimeFillerEpisodes {
        fetchFromAnimeFillerList(meta)?.let { return it }
        if (!malId.isNullOrBlank()) {
            fetchFromJikan(malId)?.let { return AnimeFillerEpisodes(fillers = it) }
        }
        return AnimeFillerEpisodes()
    }

    private suspend fun fetchFromAnimeFillerList(meta: MetaDetails): AnimeFillerEpisodes? {
        val candidates = candidateSlugs(meta.name)
        for (slug in candidates) {
            tryFetchAflSlug(slug)?.takeUnless { it.isEmpty }?.let { return it }
        }
        val matched = findSlugInShowDirectory(meta.name)
        if (matched != null && matched !in candidates) {
            return tryFetchAflSlug(matched)
        }
        return null
    }

    private suspend fun tryFetchAflSlug(slug: String): AnimeFillerEpisodes? = withTimeoutOrNull(HTTP_TIMEOUT_MS) {
        runCatching { parseAflHtml(httpGetTextWithHeaders("$AFL_BASE_URL/shows/$slug", REQUEST_HEADERS)) }
            .getOrNull()
    }

    internal fun parseAflHtml(html: String): AnimeFillerEpisodes {
        val fillers = mutableSetOf<Int>()
        val mixed = mutableSetOf<Int>()
        CONDENSED_DIV_REGEX.findAll(html).forEach { match ->
            val classAttr = match.groupValues[1]
            val target = when {
                classAttr.contains("mixed", ignoreCase = true) -> mixed
                classAttr.contains("filler", ignoreCase = true) -> fillers
                else -> return@forEach
            }
            EPISODE_RANGE_REGEX.findAll(match.groupValues[2].replace(TAG_REGEX, " ")).forEach { range ->
                val start = range.groupValues[1].toIntOrNull() ?: return@forEach
                val end = range.groupValues[2].toIntOrNull() ?: start
                if (end >= start && end - start < 2_000) target.addAll(start..end)
            }
        }
        ROW_REGEX.findAll(html).forEach { match ->
            val episode = match.groupValues[2].toIntOrNull() ?: return@forEach
            val classAttr = match.groupValues[1]
            when {
                classAttr.contains("mixed", ignoreCase = true) -> mixed += episode
                classAttr.contains("filler", ignoreCase = true) -> fillers += episode
            }
        }
        fillers.removeAll(mixed)
        return AnimeFillerEpisodes(fillers = fillers, mixed = mixed)
    }

    private suspend fun fetchFromJikan(malId: String): Set<Int>? = withTimeoutOrNull(HTTP_TIMEOUT_MS) {
        runCatching {
            val root = json.parseToJsonElement(
                httpGetTextWithHeaders("$JIKAN_BASE_URL/anime/$malId/episodes", REQUEST_HEADERS),
            ).jsonObject
            root["data"]?.jsonArray.orEmpty().mapNotNull { element ->
                val obj = element.jsonObject
                val number = obj["mal_id"]?.jsonPrimitive?.int ?: return@mapNotNull null
                number.takeIf { obj["filler"]?.jsonPrimitive?.booleanOrNull == true }
            }.toSet()
        }.getOrNull()
    }

    private suspend fun findSlugInShowDirectory(title: String): String? {
        val directory = aflShowsCache ?: withTimeoutOrNull(HTTP_TIMEOUT_MS) {
            runCatching {
                SHOW_LINK_REGEX.findAll(httpGetTextWithHeaders("$AFL_BASE_URL/shows", REQUEST_HEADERS))
                    .associate { cleanShowTitle(it.groupValues[2]) to it.groupValues[1] }
                    .also { aflShowsCache = it }
            }.getOrNull()
        } ?: return null
        val target = cleanShowTitle(title)
        if (target.isBlank()) return null
        return directory[target] ?: directory.entries.firstOrNull { (name, _) ->
            name.length > 3 && (name.contains(target) || target.contains(name))
        }?.value
    }

    private fun cleanShowTitle(title: String): String =
        title.replace(Regex("""\s*\([^)]*\)"""), "").replace(":", "").replace("-", " ").trim().lowercase()

    internal fun candidateSlugs(name: String): List<String> = buildList {
        add(slugify(name))
        if (name.contains(":")) {
            add(slugify(name.substringBefore(':')))
            add(slugify(name.replace(":", "")))
        }
    }.filter { it.isNotBlank() }.distinct()

    internal fun slugify(name: String): String =
        name.lowercase().replace("&", "and").replace(Regex("[^a-z0-9]+"), "-").trim('-')

    private fun cached(key: String): AnimeFillerEpisodes? {
        memoryCache[key]?.let { return it }
        val payload = AnimeModeStorage.loadString(CACHE_PREFIX + key) ?: return null
        fun ints(prefix: String) = payload.split("|").firstOrNull { it.startsWith(prefix) }
            ?.removePrefix(prefix)?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.toSet().orEmpty()
        return AnimeFillerEpisodes(fillers = ints("f="), mixed = ints("m="))
            .also { memoryCache[key] = it }
    }

    private fun store(key: String, episodes: AnimeFillerEpisodes) {
        memoryCache[key] = episodes
        AnimeModeStorage.saveString(
            CACHE_PREFIX + key,
            "f=${episodes.fillers.sorted().joinToString(",")}|m=${episodes.mixed.sorted().joinToString(",")}",
        )
    }

    private fun parseIdSource(id: String?): Pair<String, String>? {
        val raw = id?.trim()?.takeIf { it.isNotBlank() } ?: return null
        if (raw.startsWith("tt", ignoreCase = true)) return "imdb" to raw.substringBefore(':')
        val colon = raw.indexOf(':')
        if (colon <= 0) return null
        val prefix = raw.substring(0, colon).lowercase()
        val value = raw.substring(colon + 1).substringBefore(':').takeIf { it.isNotBlank() } ?: return null
        return when (prefix) {
            "imdb", "tmdb", "tvdb", "mal", "kitsu", "anidb", "anilist", "simkl" -> prefix to value
            else -> null
        }
    }
}
