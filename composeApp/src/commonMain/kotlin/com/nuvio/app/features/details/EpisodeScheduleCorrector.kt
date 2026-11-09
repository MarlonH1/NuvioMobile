package com.nuvio.app.features.details

import co.touchlab.kermit.Logger
import com.nuvio.app.core.time.parseEpisodeReleaseEpochMs
import com.nuvio.app.features.addons.httpGetText
import com.nuvio.app.features.watchprogress.WatchProgressClock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs

/**
 * Keeps series episode lists and release dates right, so "Upcoming" / "Airs in N days" match
 * when an episode actually comes out.
 *
 * 1. Broken lists: when an addon returns no episodes or the same episode several times (e.g.
 *    torrent-derived lists), the clean IMDb-numbered list from Cinemeta is used instead. It
 *    uses the numbering stream addons expect, so sources keep resolving.
 * 2. Release dates: each episode is checked against TVmaze, which records the real release on
 *    the streaming service (e.g. Paramount+ on Friday instead of the Sunday TV broadcast).
 *    Matched by season/episode, or, for shows numbered differently (common for anime), by a
 *    unique TVmaze episode within a few days. Unmatched episodes keep their date.
 */
internal object EpisodeScheduleCorrector {
    private val log = Logger.withTag("EpisodeSchedule")
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val mutex = Mutex()

    private const val TVMAZE = "https://api.tvmaze.com"
    private const val CINEMETA = "https://v3-cinemeta.strem.io"
    private const val TIMEOUT_MS = 6_000L
    private const val CACHE_MS = 6L * 60 * 60 * 1000
    private const val FAILURE_CACHE_MS = 30L * 60 * 1000
    private const val DAY_MS = 24L * 60 * 60 * 1000
    /** Same-numbered episode: release-platform differences are a few days at most. */
    private const val SAME_EPISODE_WINDOW_MS = 4 * DAY_MS
    /** Differently numbered: only a unique TVmaze episode this close counts as a match. */
    private const val NEARBY_WINDOW_MS = 3 * DAY_MS

    private data class Episode(val season: Int, val number: Int, val released: String, val epochMs: Long)
    private data class Cached<T>(val value: T?, val atMs: Long, val ok: Boolean)

    private val scheduleCache = mutableMapOf<String, Cached<List<Episode>>>()
    private val cinemetaCache = mutableMapOf<String, Cached<List<MetaVideo>>>()

    suspend fun correct(meta: MetaDetails, lookupId: String): MetaDetails {
        val imdbId = imdbIdOf(meta, lookupId) ?: return meta
        if (!isSeries(meta)) return meta
        return runCatching {
            val listFixed = if (needsCleanList(meta.videos)) {
                val clean = cinemetaVideos(imdbId)
                if (!clean.isNullOrEmpty()) {
                    log.d { "Replaced broken episode list of ${meta.name} (${meta.videos.size} entries) with Cinemeta (${clean.size})" }
                    meta.copy(videos = clean)
                } else {
                    meta
                }
            } else {
                meta
            }
            val schedule = tvmazeSchedule(imdbId).orEmpty()
            if (schedule.isEmpty() || listFixed.videos.isEmpty()) listFixed else applySchedule(listFixed, schedule)
        }.onFailure { log.d { "Schedule correction failed for ${meta.name}: ${it.message}" } }
            .getOrDefault(meta)
    }

    private fun isSeries(meta: MetaDetails): Boolean =
        meta.type.equals("series", ignoreCase = true) || meta.type.equals("anime", ignoreCase = true) ||
            meta.videos.any { it.season != null && it.episode != null }

    private fun imdbIdOf(meta: MetaDetails, lookupId: String): String? = listOfNotNull(meta.imdbId, meta.id, lookupId)
        .map { it.trim().substringBefore(':') }
        .firstOrNull { it.startsWith("tt") && it.length > 2 && it.drop(2).all(Char::isDigit) }

    /** No episodes, or the same season/episode listed more than once (torrent-file lists). */
    private fun needsCleanList(videos: List<MetaVideo>): Boolean {
        val numbered = videos.filter { it.season != null && it.episode != null }
        if (numbered.isEmpty()) return true
        return numbered.groupingBy { it.season to it.episode }.eachCount().values.any { it > 1 }
    }

    private fun applySchedule(meta: MetaDetails, schedule: List<Episode>): MetaDetails {
        val bySeasonEpisode = schedule.associateBy { it.season to it.number }
        var changed = 0
        val videos = meta.videos.map { video ->
            val season = video.season ?: return@map video
            val number = video.episode ?: return@map video
            val currentEpochMs = parseEpisodeReleaseEpochMs(video.released)
            val match = bySeasonEpisode[season to number]?.takeIf { candidate ->
                currentEpochMs == null || abs(candidate.epochMs - currentEpochMs) <= SAME_EPISODE_WINDOW_MS
            } ?: currentEpochMs?.let { epochMs ->
                schedule.filter { abs(it.epochMs - epochMs) <= NEARBY_WINDOW_MS }.singleOrNull()
            }
            if (match == null || match.released == video.released) return@map video
            changed++
            video.copy(released = match.released)
        }
        if (changed > 0) log.d { "Corrected $changed release dates for ${meta.name} from TVmaze" }
        return if (changed > 0) meta.copy(videos = videos) else meta
    }

    private suspend fun tvmazeSchedule(imdbId: String): List<Episode>? =
        cached(scheduleCache, imdbId) {
            val show = json.parseToJsonElement(httpGetText("$TVMAZE/lookup/shows?imdb=$imdbId")).jsonObject
            val showId = show["id"]?.jsonPrimitive?.intOrNull ?: return@cached null
            json.parseToJsonElement(httpGetText("$TVMAZE/shows/$showId/episodes")).jsonArray.mapNotNull { element ->
                val episode = element.jsonObject
                val season = episode.int("season") ?: return@mapNotNull null
                val number = episode.int("number") ?: return@mapNotNull null
                val airdate = episode.string("airdate")?.takeIf { it.length == 10 } ?: return@mapNotNull null
                // Streaming releases come without a time; TVmaze then fills a placeholder
                // time, so keep the plain date (shown as that calendar day everywhere).
                val released = if (episode.string("airtime").isNullOrBlank()) {
                    airdate
                } else {
                    episode.string("airstamp") ?: airdate
                }
                val epochMs = parseEpisodeReleaseEpochMs(released) ?: return@mapNotNull null
                Episode(season, number, released, epochMs)
            }
        }

    private suspend fun cinemetaVideos(imdbId: String): List<MetaVideo>? =
        cached(cinemetaCache, imdbId) {
            MetaDetailsParser.parse(httpGetText("$CINEMETA/meta/series/$imdbId.json")).videos
                .takeIf { it.isNotEmpty() }
        }

    private suspend fun <T> cached(
        cache: MutableMap<String, Cached<T>>,
        key: String,
        load: suspend () -> T?,
    ): T? {
        val now = WatchProgressClock.nowEpochMs()
        mutex.withLock {
            cache[key]?.let { entry ->
                val maxAge = if (entry.ok) CACHE_MS else FAILURE_CACHE_MS
                if (now - entry.atMs < maxAge) return entry.value
            }
        }
        val value = withTimeoutOrNull(TIMEOUT_MS) { runCatching { load() }.getOrNull() }
        mutex.withLock { cache[key] = Cached(value, now, ok = value != null) }
        return value
    }

    private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull
    private fun JsonObject.string(key: String): String? =
        this[key]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
}
