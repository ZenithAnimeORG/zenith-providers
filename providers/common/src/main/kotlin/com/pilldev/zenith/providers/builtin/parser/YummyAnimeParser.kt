package com.pilldev.zenith.providers.builtin.parser
import com.pilldev.zenith.domain.model.EpisodeSource
import com.pilldev.zenith.domain.model.TranslationType
import com.pilldev.zenith.domain.model.VideoSource
import com.pilldev.zenith.provider.model.BuiltInProviders
import com.pilldev.zenith.providers.builtin.api.YummyAnimeApi
import com.pilldev.zenith.providers.builtin.api.YummyAnimeResult
import com.pilldev.zenith.providers.builtin.api.YummyVideo
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

private val PLAYER_PREFIX_REGEX = Regex("(?i)^(?:Плеер\\s+)?(?:Kodik|Sibnet|CVH|Aksor|Alloha|Плеер)\\s*")
private val CLEANING_REGEX = Regex("(?i)\\s*\\(?\\b(Озвучка|Дубляж|Dub|Dubbing|Voice|Voiceover|VO)\\b\\)?\\s*")

/**
 * Parser for fetching anime media sources from YummyAnime.
 */
open class YummyAnimeParser
    constructor(
        /**
         * The YummyAnime API client interface.
         */
        public val yummyAnimeApi: YummyAnimeApi,
        private val json: Json,
        private val playerSettingsManager: com.pilldev.zenith.domain.repository.PlayerSettingsRepository? = null,
        private val appDispatchers: com.pilldev.zenith.domain.repository.AppDispatchers,
    ) : AnimeParser {
        override val name: String = "YummyAnime"

        /**
         * Fetches available video sources for a specific anime.
         */
        public override suspend fun getSources(
            shikimoriId: Int,
            animeName: String,
            russianName: String?,
        ): ParserResult =
            kotlinx.coroutines.withContext(
                appDispatchers.io,
            ) {
                val startTime = kotlin.time.Clock.System
                    .now()
                    .toEpochMilliseconds()
                co.touchlab.kermit.Logger
                    .withTag("Zenith")
                    .e { "YummyAnimeParser: Getting sources for $animeName / $russianName" }
                val sources = mutableListOf<VideoSource>()
                try {
                    val queries = mutableListOf(animeName)
                    russianName?.let { queries.add(it) }

                    var result: YummyAnimeResult? = null
                    val distinctQueries = queries.toMutableList()
                    val firstTwoWords = animeName.split(" ").take(2).joinToString(" ")
                    if (firstTwoWords.length > 5) distinctQueries.add(firstTwoWords)

                    val uniqueQueries = distinctQueries.distinct()

                    coroutineScope {
                        val searchJobs =
                            uniqueQueries.map { query ->
                                async {
                                    co.touchlab.kermit.Logger
                                        .withTag("Zenith")
                                        .e { "YummyAnimeParser: Searching for '$query'" }
                                    try {
                                        val searchElement = yummyAnimeApi.search(query)
                                        val results = parseSearchResults(searchElement)
                                        results
                                    } catch (e: Exception) {
                                        null as List<YummyAnimeResult>?
                                    }
                                }
                            }

                        for ((index, job) in searchJobs.withIndex()) {
                            val results = job.await() ?: continue
                            result = results.find { it.remote_ids?.shikimori_id == shikimoriId } ?: results.find {
                                it.title.contains(animeName, ignoreCase = true) ||
                                    (russianName != null && it.title.contains(russianName, ignoreCase = true))
                            } ?: results.firstOrNull()
                            if (result != null) {
                                searchJobs.drop(index + 1).forEach { it.cancel() }
                                break
                            }
                        }
                    }

                    result?.let { anime ->
                        val videosElement = yummyAnimeApi.getVideos(anime.anime_id)
                        val videos = parseVideos(videosElement)
                        co.touchlab.kermit.Logger.withTag("Zenith").d {
                            "YummyAnimeParser: Parsed ${videos.size} videos. Episodes present: ${videos.map { it.number }.distinct()}"
                        }

                        // Group by translation + player
                        val translationMap = mutableMapOf<String, MutableList<EpisodeSource>>()

                        videos.forEach { video ->
                            val embed = video.iframe_url?.ifBlank { null } ?: video.url?.ifBlank { null }
                            if (!embed.isNullOrBlank()) {
                                val dubbing = when (val a = video.author) {
                                    is kotlinx.serialization.json.JsonPrimitive -> a.content.ifBlank { null }
                                    is JsonObject -> a["name"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else null }
                                        ?: a["title"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else null }
                                    else -> null
                                } ?: video.data?.dubbing?.ifBlank { null } ?: "По умолчанию"
                                val player = video.player?.ifBlank { null } ?: video.data?.player?.ifBlank { null } ?: "Плеер"
                                val name = "$player ($dubbing)"

                                val list = translationMap.getOrPut(name) { mutableListOf() }
                                val embedUrl = if (embed.startsWith("//")) "https:$embed" else embed
                                val episodeNum = when (val n = video.number) {
                                    is kotlinx.serialization.json.JsonPrimitive -> n.content.toIntOrNull() ?: 1
                                    else -> 1
                                }
                                list.add(EpisodeSource(episodeNum, embedUrl))
                            }
                        }

                        translationMap.forEach { (fullName, episodeList) ->
                            val firstUrl = episodeList.firstOrNull()?.url ?: ""
                            val providerId =
                                when {
                                    fullName.contains("Kodik", ignoreCase = true) -> BuiltInProviders.KODIK
                                    fullName.contains("Sibnet", ignoreCase = true) -> BuiltInProviders.SIBNET
                                    fullName.contains("CVH", ignoreCase = true) -> BuiltInProviders.CVH
                                    fullName.contains("Aksor", ignoreCase = true) -> BuiltInProviders.AKSOR
                                    fullName.contains("Alloha", ignoreCase = true) -> BuiltInProviders.ALLOHA
                                    firstUrl.contains("sibnet", ignoreCase = true) -> BuiltInProviders.SIBNET
                                    else -> BuiltInProviders.KODIK
                                }
                            val playerName =
                                when (providerId) {
                                    BuiltInProviders.KODIK -> "Kodik"
                                    BuiltInProviders.SIBNET -> "Sibnet"
                                    BuiltInProviders.CVH -> "CVH"
                                    BuiltInProviders.AKSOR -> "Aksor"
                                    BuiltInProviders.ALLOHA -> "Alloha"
                                    else -> "Плеер"
                                }

                            val rawTranslationName =
                                fullName
                                    .replace(PLAYER_PREFIX_REGEX, "")
                                    .replace("()", "")
                                    .trim()
                                    .removeSurrounding("(", ")")
                                    .ifBlank { "Оригинал" }

                            val translationName =
                                rawTranslationName
                                    .replace(CLEANING_REGEX, " ")
                                    .trim()
                                    .ifBlank { "Оригинал" }

                            val tType =
                                when {
                                    fullName.contains(
                                        "Субтитры",
                                        ignoreCase = true,
                                    ) ||
                                        fullName.contains("Sub", ignoreCase = true) -> TranslationType.SUB
                                    fullName.contains(
                                        "Дубляж",
                                        ignoreCase = true,
                                    ) ||
                                        fullName.contains("Dub", ignoreCase = true) -> TranslationType.DUB
                                    else -> TranslationType.VO
                                }

                            val isKodik = playerName == "Kodik"
                            val isKodikActive = playerSettingsManager?.activeSources?.value?.contains("Kodik") ?: true
                            val isYummyPlayerActive =
                                playerSettingsManager?.yummyAnimeInternalPlayers?.value?.contains(
                                    playerName,
                                ) ?: true
                            val shouldAdd = if (isKodik) isKodikActive else isYummyPlayerActive
                            if (shouldAdd) {
                                val videoSource =
                                    VideoSource(
                                        name = if (isKodik) "Kodik (Yummy)" else playerName,
                                        translationName = translationName,
                                        translationType = tType,
                                        providerId = providerId,
                                        episodes = episodeList.distinctBy { it.number }.sortedBy { it.number },
                                    )
                                sources.add(videoSource)
                            }
                        }

                        val duration = kotlin.time.Clock.System
                            .now()
                            .toEpochMilliseconds() - startTime
                        co.touchlab.kermit.Logger.withTag("Zenith").i {
                            "YummyAnimeParser: Finished in ${duration}ms, returned ${sources.size} sources. Players: ${sources.map {
                                it.name
                            }.distinct()}"
                        }
                    }
                } catch (e: Exception) {
                    co.touchlab.kermit.Logger
                        .withTag("Zenith")
                        .e(e) { "YummyAnimeParser: Error" }
                    return@withContext ParserResult(sources, true)
                }
                ParserResult(sources, false)
            }

        /**
         * Parses the search results from raw JSON element returned by YummyAnime.
         */
        public fun parseSearchResults(element: JsonElement): List<YummyAnimeResult> =
            try {
                when (element) {
                    is JsonObject -> {
                        val res = element["response"] ?: element["results"] ?: element["data"]
                        if (res is JsonArray) {
                            res.mapNotNull {
                                try {
                                    json.decodeFromJsonElement<YummyAnimeResult>(
                                        it,
                                    )
                                } catch (e: Exception) {
                                    null
                                }
                            }
                        } else {
                            emptyList()
                        }
                    }
                    is JsonArray -> {
                        element.mapNotNull {
                            try {
                                json.decodeFromJsonElement<YummyAnimeResult>(
                                    it,
                                )
                            } catch (e: Exception) {
                                null
                            }
                        }
                    }
                    else -> emptyList()
                }
            } catch (e: Exception) {
                emptyList()
            }

        private fun parseVideos(element: JsonElement): List<YummyVideo> =
            try {
                when (element) {
                    is JsonArray -> {
                        element.mapNotNull {
                            try {
                                json.decodeFromJsonElement<YummyVideo>(
                                    it,
                                )
                            } catch (e: Exception) {
                                null
                            }
                        }
                    }
                    is JsonObject -> {
                        val res = element["response"] ?: element["results"] ?: element["data"]
                        if (res is JsonArray) {
                            res.mapNotNull {
                                try {
                                    json.decodeFromJsonElement<YummyVideo>(
                                        it,
                                    )
                                } catch (e: Exception) {
                                    null
                                }
                            }
                        } else {
                            emptyList()
                        }
                    }
                    else -> emptyList()
                }
            } catch (e: Exception) {
                emptyList()
            }
    }
