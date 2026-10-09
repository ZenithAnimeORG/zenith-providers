package com.pilldev.zenith.providers.builtin.parser

import com.fleeksoft.ksoup.Ksoup
import com.pilldev.zenith.domain.model.EpisodeSource
import com.pilldev.zenith.domain.model.TranslationType
import com.pilldev.zenith.domain.model.VideoSource
import com.pilldev.zenith.domain.repository.PlayerSettingsRepository
import com.pilldev.zenith.provider.model.BuiltInProviders
import com.pilldev.zenith.provider.model.SkipInterval
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLQueryComponent
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

open class AnitypeParser
    constructor(
        private val client: HttpClient,
        private val json: Json,
        private val playerSettingsManager: PlayerSettingsRepository? = null,
        private val appDispatchers: com.pilldev.zenith.domain.repository.AppDispatchers,
    ) : AnimeParser {
        override val name: String = "AniType"

        override suspend fun getSources(
            shikimoriId: Int,
            animeName: String,
            russianName: String?,
        ): ParserResult =
            withContext(
                appDispatchers.io,
            ) {
                co.touchlab.kermit.Logger
                    .withTag("Zenith")
                    .d { "AnitypeParser: Getting sources for $animeName (ID: $shikimoriId)" }
                val sources = mutableListOf<VideoSource>()

                try {
                    val url = "${com.pilldev.zenith.providers.builtin.net.BuiltInEndpoints.ANITYPE}play/$shikimoriId"
                    val response =
                        client.get(url) {
                            header("User-Agent", USER_AGENT)
                            timeout {
                                requestTimeoutMillis = 4000L
                                socketTimeoutMillis = 4000L
                                connectTimeoutMillis = 4000L
                            }
                        }
                    if (response.status != HttpStatusCode.OK) {
                        return@withContext ParserResult(sources, false)
                    }

                    val html = response.bodyAsText()
                    val doc = Ksoup.parse(html)

                    // Extract all script tags containing __next_f.push
                    val scripts = doc.select("script")
                    var concatenated = ""
                    for (script in scripts) {
                        val content = script.data()
                        if (content.contains("__next_f.push")) {
                            // Match __next_f.push([1,"..."])
                            val pattern = """__next_f\.push\(\[\d+,\s*"(.*)"\]\)"""
                            val regex = Regex(pattern, RegexOption.DOT_MATCHES_ALL)
                            val match = regex.find(content)
                            if (match != null) {
                                val escapedStr = match.groupValues[1]
                                concatenated += unescapeJsString(escapedStr)
                            }
                        }
                    }

                    if (concatenated.isBlank()) {
                        co.touchlab.kermit.Logger
                            .withTag("Zenith")
                            .e { "AnitypeParser: Concatenated Next.js RSC payload is empty" }
                        return@withContext ParserResult(sources, false)
                    }

                    // Search for "otherAnimeInfo" in the concatenated payload
                    val term = "otherAnimeInfo"
                    val pos = concatenated.indexOf(term)
                    if (pos != -1) {
                        val startIdx = concatenated.indexOf('[', pos)
                        if (startIdx != -1) {
                            var brackets = 0
                            var endIdx = -1
                            for (i in startIdx until concatenated.length) {
                                val char = concatenated[i]
                                if (char == '[') {
                                    brackets++
                                } else if (char == ']') {
                                    brackets--
                                    if (brackets == 0) {
                                        endIdx = i + 1
                                        break
                                    }
                                }
                            }

                            if (endIdx != -1) {
                                val jsonStr = concatenated.substring(startIdx, endIdx)
                                try {
                                    val jsonArray = json.parseToJsonElement(jsonStr).jsonArray
                                    val episodesByTranslation = mutableMapOf<String, MutableList<EpisodeSource>>()

                                    for (element in jsonArray) {
                                        val obj = element.jsonObject
                                        val episodeNum = obj["episode"]?.jsonPrimitive?.intOrNull ?: 1
                                        val translationObj = obj["translation"]?.jsonObject
                                        val titleJson = translationObj?.get("title")?.jsonPrimitive
                                        val translationTitle = titleJson?.content ?: "Original"

                                        val linksObj = obj["links"]?.jsonObject
                                        val kodikUrlRaw =
                                            linksObj
                                                ?.get("kodik")
                                                ?.jsonPrimitive
                                                ?.content
                                                .orEmpty()

                                        if (translationObj != null && linksObj != null && kodikUrlRaw.isNotBlank()) {
                                            var kodikUrl = kodikUrlRaw
                                            if (kodikUrl.startsWith("//")) {
                                                kodikUrl = "https:$kodikUrl"
                                            }

                                            // Construct AniType source play URL carrying translation metadata and kodik fallback URL
                                            val tParam = translationTitle.encodeURLQueryComponent()
                                            val kParam = kodikUrl.encodeURLQueryComponent()
                                            val playUrl =
                                                "${com.pilldev.zenith.providers.builtin.net.BuiltInEndpoints.ANITYPE}play/$shikimoriId/$episodeNum" +
                                                    "?translation=$tParam&kodik=$kParam"

                                            episodesByTranslation
                                                .getOrPut(translationTitle) { mutableListOf() }
                                                .add(EpisodeSource(episodeNum, playUrl))
                                        }
                                    }

                                    for ((translationTitle, episodes) in episodesByTranslation) {
                                        val isSub =
                                            translationTitle.contains("Субтитры", ignoreCase = true) ||
                                                translationTitle.contains("Sub", ignoreCase = true)
                                        val tType = if (isSub) TranslationType.SUB else TranslationType.VO
                                        val source =
                                            VideoSource(
                                                name = "Kodik (AniType)",
                                                translationName = translationTitle,
                                                translationType = tType,
                                                providerId = BuiltInProviders.ANITYPE,
                                                episodes = episodes.sortedBy { it.number },
                                            )
                                        sources.add(source)
                                    }
                                } catch (e: Exception) {
                                    co.touchlab.kermit.Logger
                                        .withTag("Zenith")
                                        .e(e) { "AnitypeParser: Error parsing otherAnimeInfo JSON" }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    co.touchlab.kermit.Logger
                        .withTag("Zenith")
                        .e(e) { "AnitypeParser: Error parsing sources for $shikimoriId" }
                    return@withContext ParserResult(sources, true)
                }

                ParserResult(sources, false)
            }

        open suspend fun getSkips(
            shikimoriId: Int,
            episodeNumber: Int,
            translationName: String? = null,
        ): List<SkipInterval> =
            withContext(appDispatchers.io) {
                try {
                    val url = "${com.pilldev.zenith.providers.builtin.net.BuiltInEndpoints.ANITYPE}skips/find/$shikimoriId/$episodeNumber"
                    val response =
                        client.get(url) {
                            header("User-Agent", USER_AGENT)
                            timeout {
                                requestTimeoutMillis = 4000L
                                socketTimeoutMillis = 4000L
                                connectTimeoutMillis = 4000L
                            }
                        }
                    if (response.status != HttpStatusCode.OK) return@withContext emptyList()
                    val root = json.parseToJsonElement(response.bodyAsText()).jsonArray
                    root.mapNotNull { element ->
                        val obj = element.jsonObject
                        val from = obj["from"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                        val to = obj["to"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                        val trans = obj["translation"]?.jsonPrimitive?.content
                        if (!translationName.isNullOrBlank() && trans != null && !trans.equals(translationName, ignoreCase = true)) {
                            return@mapNotNull null
                        }
                        val moment = obj["momentType"]?.jsonPrimitive?.content?.lowercase()
                        val skipType =
                            when (moment) {
                                "опенинг", "op", "opening" -> "op"
                                "эндинг", "ed", "ending" -> "ed"
                                else -> "op"
                            }
                        SkipInterval(
                            startTime = from.toDouble(),
                            endTime = to.toDouble(),
                            skipType = skipType,
                        )
                    }
                } catch (e: Exception) {
                    co.touchlab.kermit.Logger
                        .withTag("AnitypeParser")
                        .w(e) { "Failed to fetch skips for $shikimoriId ep $episodeNumber" }
                    emptyList()
                }
            }

        private fun unescapeJsString(s: String): String =
            s
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\/", "/")
                .replace("\\n", "\n")
                .replace("\\t", "\t")
    }
