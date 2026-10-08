package com.pilldev.zenith.providers.builtin.parser

import com.pilldev.zenith.domain.model.EpisodeSource
import com.pilldev.zenith.domain.model.TranslationType
import com.pilldev.zenith.domain.model.VideoSource
import com.pilldev.zenith.provider.model.BuiltInProviders
import com.pilldev.zenith.providers.builtin.api.KodikApi
import com.pilldev.zenith.providers.builtin.model.KodikResult
import com.pilldev.zenith.providers.builtin.net.BuiltInSecrets
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

open class KodikParser
    constructor(
        private val kodikApi: KodikApi,
        private val json: Json,
        private val playerSettingsManager: com.pilldev.zenith.domain.repository.PlayerSettingsRepository? = null,
        private val appDispatchers: com.pilldev.zenith.domain.repository.AppDispatchers,
    ) : AnimeParser {
        override val name: String = "Kodik"

        private val mainToken: String get() = playerSettingsManager?.effectiveKodikToken ?: BuiltInSecrets.KODIK_TOKEN

        private val fallbackTokens =
            listOf(
                "8388916361a86851b228b329c32a76f2",
                "530018f3a39e8e250f61206c9e9929da",
            )

        override suspend fun getSources(
            shikimoriId: Int,
            animeName: String,
            russianName: String?,
        ): ParserResult =
            withContext(appDispatchers.io) {
                co.touchlab.kermit.Logger
                    .withTag("Zenith")
                    .d { "KodikParser: Getting sources for $animeName (ID: $shikimoriId)" }
                val sources = mutableListOf<VideoSource>()

                try {
                    // 1. Try search by Shikimori ID
                    val results = search(shikimoriId = shikimoriId)
                    if (results.isNotEmpty()) {
                        sources.addAll(processResults(results))
                    }

                    // 2. If no results, try search by title
                    if (sources.isEmpty()) {
                        val titleResults = search(title = animeName)
                        if (titleResults.isNotEmpty()) {
                            sources.addAll(processResults(titleResults))
                        } else if (!russianName.isNullOrBlank()) {
                            val russianResults = search(title = russianName)
                            if (russianResults.isNotEmpty()) {
                                sources.addAll(processResults(russianResults))
                            }
                        }
                    }
                } catch (e: Exception) {
                    return@withContext ParserResult(sources, true)
                }

                ParserResult(sources, false)
            }

        private suspend fun search(
            shikimoriId: Int? = null,
            title: String? = null,
        ): List<KodikResult> {
            co.touchlab.kermit.Logger.withTag("Zenith").e {
                "KodikParser: Searching by ${if (shikimoriId != null) "ID: $shikimoriId" else "Title: $title"}"
            }

            val tokens = mutableListOf(mainToken)
            tokens.addAll(fallbackTokens)

            for (token in tokens) {
                try {
                    val element =
                        if (shikimoriId != null) {
                            kodikApi.searchByShikimori(token, shikimoriId)
                        } else if (title != null) {
                            kodikApi.searchByTitle(token, title)
                        } else {
                            break
                        }

                    val results = parseResults(element)
                    if (results.isNotEmpty()) {
                        co.touchlab.kermit.Logger
                            .withTag("Zenith")
                            .e { "KodikParser: Found ${results.size} results with token $token" }
                        return results
                    }
                } catch (e: Exception) {
                    co.touchlab.kermit.Logger
                        .withTag("Zenith")
                        .e(e) { "KodikParser: Token $token failed" }
                    continue
                }
            }
            co.touchlab.kermit.Logger
                .withTag("Zenith")
                .e { "KodikParser: No results found for ${shikimoriId ?: title}" }
            return emptyList()
        }

        private fun parseResults(element: JsonElement): List<KodikResult> =
            try {
                when (element) {
                    is JsonObject -> {
                        if (element.containsKey("error")) {
                            val errorMsg = element["error"].toString()
                            co.touchlab.kermit.Logger
                                .withTag("Zenith")
                                .e { "KodikParser: API error response: $errorMsg" }
                        }
                        val results = element["results"]
                        if (results is JsonArray) {
                            results.mapNotNull {
                                try {
                                    json.decodeFromJsonElement<KodikResult>(it)
                                } catch (e: Exception) {
                                    null
                                }
                            }
                        } else if (element.containsKey("link")) {
                            listOf(json.decodeFromJsonElement<KodikResult>(element))
                        } else {
                            emptyList()
                        }
                    }
                    is JsonArray -> {
                        element.mapNotNull {
                            try {
                                json.decodeFromJsonElement<KodikResult>(it)
                            } catch (e: Exception) {
                                null
                            }
                        }
                    }
                    else -> emptyList()
                }
            } catch (e: Exception) {
                co.touchlab.kermit.Logger
                    .withTag("Zenith")
                    .e(e) { "KodikParser: Error parsing results" }
                emptyList()
            }

        private fun processResults(results: List<KodikResult>): List<VideoSource> {
            val cleaningRegex = Regex("(?i)\\s*\\(?\\b(Озвучка|Дубляж|Dub|Dubbing|Voice|Voiceover|VO)\\b\\)?\\s*")

            return results
                .mapNotNull { result ->
                    val episodes = mutableListOf<EpisodeSource>()

                    if (result.seasons != null) {
                        co.touchlab.kermit.Logger.withTag("Zenith").d {
                            "KodikParser: Found seasons map for result ${result.link}. Season counts: ${result.seasons.size}"
                        }
                        result.seasons.values.forEach { season ->
                            season.episodes.forEach { (num, link) ->
                                val episodeUrl = if (link.startsWith("//")) "https:$link" else link
                                episodes.add(EpisodeSource(num.toIntOrNull() ?: 1, episodeUrl))
                            }
                        }
                    } else {
                        co.touchlab.kermit.Logger.withTag("Zenith").d {
                            "KodikParser: No seasons map for result ${result.link}, using link as episode 1. lastEpisode=${result.lastEpisode}, episodesCount=${result.episodesCount}"
                        }
                        val iframeUrl = if (result.link.startsWith("//")) "https:${result.link}" else result.link
                        episodes.add(EpisodeSource(1, iframeUrl))
                    }

                    if (episodes.isEmpty()) return@mapNotNull null

                    val rawTranslationName = result.translation?.title ?: result.title
                    val translationName = rawTranslationName.replace(cleaningRegex, " ").trim().ifBlank { "Оригинал" }

                    val tType =
                        when (result.translation?.type) {
                            "voice" -> TranslationType.VO
                            "dubbing" -> TranslationType.DUB
                            else ->
                                if (translationName.contains("Субтитры", ignoreCase = true) ||
                                    translationName.contains("Sub", ignoreCase = true)
                                ) {
                                    TranslationType.SUB
                                } else {
                                    TranslationType.VO
                                }
                        }

                    VideoSource(
                        name = "Kodik",
                        translationName = translationName,
                        translationType = tType,
                        providerId = BuiltInProviders.KODIK,
                        episodes = episodes.sortedBy { it.number },
                    )
                }.sortedByDescending { it.episodes.size }
                .distinctBy { it.translationName + it.translationType.toString() }
        }
    }
