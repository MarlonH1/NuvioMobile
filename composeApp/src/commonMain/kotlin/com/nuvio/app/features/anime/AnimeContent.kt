package com.nuvio.app.features.anime

import com.nuvio.app.features.details.MetaDetails

/** Lightweight anime detection from metadata, ported from NuvioCodeineXO's MediaClassifier. */
object AnimeContent {
    private val ANIME_ID_PREFIXES = listOf("kitsu:", "mal:", "anidb:", "anilist:")
    private val ANIMATION_GENRES = setOf("animation", "anime")

    fun isAnime(
        type: String?,
        id: String?,
        genres: List<String> = emptyList(),
        country: String? = null,
        language: String? = null,
    ): Boolean {
        if (type?.trim().equals("anime", ignoreCase = true)) return true
        val trimmedId = id?.trim().orEmpty()
        if (ANIME_ID_PREFIXES.any { trimmedId.startsWith(it, ignoreCase = true) }) return true
        val normalizedGenres = genres.map { it.trim().lowercase() }
        if ("anime" in normalizedGenres) return true
        val animated = normalizedGenres.any { it in ANIMATION_GENRES }
        return animated && isJapaneseOrigin(country, language)
    }

    fun isAnime(meta: MetaDetails?): Boolean = meta != null &&
        isAnime(meta.type, meta.id, meta.genres, meta.country, meta.language)

    // Fansub / anime release groups (from NuvioCodeineXO's MediaClassifier).
    private val ANIME_RELEASE_TAG = Regex(
        """(?i)\[\s*(SubsPlease|Erai[-_ ]raws|Judas|HorribleSubs|ASW|SSA|Commie|EMBER|Golumpa|Tsundere|PuyaSubs!?|DB|LostYears|ToonsHub|NanDesuKa|DKB|Yameii|VipAnime|Anime Time|YND)\s*]""",
    )

    /** True when a stream's filename or title carries an anime release-group tag. */
    fun isAnimeRelease(vararg texts: String?): Boolean =
        texts.any { !it.isNullOrBlank() && ANIME_RELEASE_TAG.containsMatchIn(it) }

    fun isJapaneseOrigin(country: String?, language: String?): Boolean {
        fun tokens(value: String?) = value.orEmpty().trim().lowercase().split(Regex("[,;/|\\s]+"))
        return tokens(country).any { it in setOf("jp", "jpn", "japan") } ||
            tokens(language).any { it.substringBefore('-').substringBefore('_') in setOf("ja", "jpn", "japanese") }
    }
}
