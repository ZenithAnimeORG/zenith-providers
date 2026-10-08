package com.pilldev.zenith.providers.builtin.parser
import com.pilldev.zenith.domain.model.EpisodeSource
import com.pilldev.zenith.domain.model.TranslationType
import com.pilldev.zenith.domain.model.VideoSource
import com.pilldev.zenith.domain.repository.AppDispatchers
import com.pilldev.zenith.provider.matcher.AnimeTitleMatcher
import com.pilldev.zenith.provider.model.BuiltInProviders
import com.pilldev.zenith.providers.builtin.api.AniLibertyApi
import com.pilldev.zenith.providers.builtin.api.AniLibertyRelease
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

open class AniLibriaParser
    constructor(
        val aniLibertyApi: AniLibertyApi,
        private val json: Json,
        private val appDispatchers: com.pilldev.zenith.domain.repository.AppDispatchers,
    ) : AnimeParser {
        override val name: String = "AniLibria"

        override suspend fun getSources(
            shikimoriId: Int,
            animeName: String,
            russianName: String?,
        ): ParserResult =
            kotlinx.coroutines.withContext(
                appDispatchers.io,
            ) {
                co.touchlab.kermit.Logger
                    .withTag("Zenith")
                    .e { "AniLibriaParser: Getting sources for $animeName / $russianName" }
                val sources = mutableListOf<VideoSource>()
                try {
                    val searchQueries = mutableListOf<String>()
                    searchQueries.add(animeName)
                    russianName?.let { searchQueries.add(it) }

                    val cleanName = AnimeTitleMatcher.cleanForSearch(animeName)
                    if (cleanName.isNotEmpty() && cleanName != animeName) searchQueries.add(cleanName)

                    russianName?.let {
                        val cleanRussian = AnimeTitleMatcher.cleanForSearch(it)
                        if (cleanRussian.isNotEmpty() && cleanRussian != it) searchQueries.add(cleanRussian)
                    }

                    // Try very broad search if everything else fails
                    val firstWord = animeName.split(" ").firstOrNull() ?: ""
                    if (firstWord.length > 3) searchQueries.add(firstWord)

                    var episodesList: List<com.pilldev.zenith.providers.builtin.api.AniLibertyEpisode>? = null

                    for (query in searchQueries.distinct()) {
                        co.touchlab.kermit.Logger
                            .withTag("Zenith")
                            .e { "AniLibriaParser: Searching for '$query'" }
                        val results = search(query)
                        co.touchlab.kermit.Logger
                            .withTag("Zenith")
                            .e { "AniLibriaParser: Found ${results.size} results for '$query'" }
                        for (release in results) {
                            co.touchlab.kermit.Logger
                                .withTag(
                                    "Zenith",
                                ).e { "AniLibriaParser: Checking release ${release.id} - ${release.name?.main}" }
                            var currentEpisodes = release.episodes
                            if (currentEpisodes.isNullOrEmpty()) {
                                co.touchlab.kermit.Logger
                                    .withTag(
                                        "Zenith",
                                    ).e { "AniLibriaParser: Episodes empty, fetching full release ${release.id}" }
                                try {
                                    val fullReleaseElement = aniLibertyApi.getRelease(release.id.toString())
                                    val fullRelease = parseSingleResult(fullReleaseElement)
                                    currentEpisodes = fullRelease?.episodes
                                } catch (e: Exception) {
                                    co.touchlab.kermit.Logger
                                        .withTag("Zenith")
                                        .e(e) { "AniLibriaParser: Error fetching full release" }
                                    continue
                                }
                            }

                            if (!currentEpisodes.isNullOrEmpty()) {
                                co.touchlab.kermit.Logger
                                    .withTag(
                                        "Zenith",
                                    ).i { "AniLibriaParser: Found ${currentEpisodes.size} episodes in release ${release.id}" }
                                episodesList = currentEpisodes
                                break
                            }
                        }
                        if (!episodesList.isNullOrEmpty()) break
                    }

                    episodesList?.let { list ->
                        val episodes =
                            list
                                .map { ep ->
                                    val url = ep.hls_1080 ?: ep.hls_720 ?: ep.hls_480 ?: ""
                                    val qualitiesMap = mutableMapOf<String, String>()
                                    ep.hls_1080?.let {
                                        if (it.isNotEmpty()) {
                                            qualitiesMap["1080p"] =
                                                if (it.startsWith("//")) {
                                                    "https:$it"
                                                } else if (it.startsWith("http")) {
                                                    it
                                                } else {
                                                    "https://anilibria.top$it"
                                                }
                                        }
                                    }
                                    ep.hls_720?.let {
                                        if (it.isNotEmpty()) {
                                            qualitiesMap["720p"] =
                                                if (it.startsWith("//")) {
                                                    "https:$it"
                                                } else if (it.startsWith("http")) {
                                                    it
                                                } else {
                                                    "https://anilibria.top$it"
                                                }
                                        }
                                    }
                                    ep.hls_480?.let {
                                        if (it.isNotEmpty()) {
                                            qualitiesMap["480p"] =
                                                if (it.startsWith("//")) {
                                                    "https:$it"
                                                } else if (it.startsWith("http")) {
                                                    it
                                                } else {
                                                    "https://anilibria.top$it"
                                                }
                                        }
                                    }

                                    EpisodeSource(
                                        number = ep.ordinal.toInt(),
                                        url =
                                            when {
                                                url.startsWith("//") -> "https:$url"
                                                url.startsWith("http") -> url
                                                url.isNotEmpty() -> "https://anilibria.top$url"
                                                else -> ""
                                            },
                                        quality = if (qualitiesMap.isNotEmpty()) qualitiesMap else null,
                                    )
                                }.filter { it.url.isNotEmpty() }

                        if (episodes.isNotEmpty()) {
                            sources.add(
                                VideoSource(
                                    name = "AniLibria",
                                    translationName = "AniLibria",
                                    translationType = TranslationType.VO,
                                    providerId = BuiltInProviders.ANILIBRIA,
                                    episodes = episodes,
                                ),
                            )
                        }
                    }
                } catch (e: Exception) {
                    co.touchlab.kermit.Logger
                        .withTag("Zenith")
                        .e(e) { "Error in AniLibriaParser.getSources" }
                    return@withContext ParserResult(sources, true)
                }
                ParserResult(sources, false)
            }

        private suspend fun search(query: String): List<AniLibertyRelease> =
            try {
                val element = aniLibertyApi.appSearch(query)
                co.touchlab.kermit.Logger
                    .withTag("Zenith")
                    .d { "AniLibriaParser: appSearch('$query') raw response: $element" }
                parseResults(element).ifEmpty {
                    val catalogElement = aniLibertyApi.searchReleases(query)
                    co.touchlab.kermit.Logger
                        .withTag("Zenith")
                        .d { "AniLibriaParser: searchReleases('$query') raw response: $catalogElement" }
                    parseResults(catalogElement)
                }
            } catch (e: Exception) {
                co.touchlab.kermit.Logger
                    .withTag("Zenith")
                    .e(e) { "AniLibriaParser: Error searching for '$query'" }
                emptyList()
            }

        fun parseResults(element: JsonElement): List<AniLibertyRelease> =
            try {
                when (element) {
                    is JsonArray ->
                        element.filterIsInstance<JsonObject>().mapNotNull {
                            runCatching { json.decodeFromJsonElement<AniLibertyRelease>(it) }.getOrNull()
                        }
                    is JsonObject -> {
                        val data = element["data"]
                        if (data is JsonArray) {
                            data.filterIsInstance<JsonObject>().mapNotNull {
                                runCatching { json.decodeFromJsonElement<AniLibertyRelease>(it) }.getOrNull()
                            }
                        } else {
                            emptyList()
                        }
                    }
                    else -> emptyList()
                }
            } catch (_: Exception) {
                emptyList()
            }

        fun parseSingleResult(element: JsonElement): AniLibertyRelease? =
            try {
                if (element is JsonObject) {
                    val data = element["data"] ?: element
                    if (data is JsonObject) {
                        json.decodeFromJsonElement<AniLibertyRelease>(data)
                    } else {
                        null
                    }
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }
    }
