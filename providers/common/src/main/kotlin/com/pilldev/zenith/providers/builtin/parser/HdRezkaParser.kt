package com.pilldev.zenith.providers.builtin.parser

import com.fleeksoft.ksoup.Ksoup
import com.pilldev.zenith.domain.model.EpisodeSource
import com.pilldev.zenith.domain.model.HdRezkaStream
import com.pilldev.zenith.domain.model.TranslationType
import com.pilldev.zenith.domain.model.VideoSource
import com.pilldev.zenith.domain.repository.HdRezkaParserRepository
import com.pilldev.zenith.provider.matcher.AnimeTitleMatcher
import com.pilldev.zenith.provider.model.BuiltInProviders
import com.pilldev.zenith.providers.builtin.api.HdRezkaApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

open class HdRezkaParser
    constructor(
        val hdRezkaApi: HdRezkaApi,
        private val json: Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        },
    ) : AnimeParser,
        com.pilldev.zenith.domain.repository.HdRezkaParserRepository {
        override val name: String = "HDRezka"

        override suspend fun getSources(
            shikimoriId: Int,
            animeName: String,
            russianName: String?,
        ): ParserResult {
            co.touchlab.kermit.Logger
                .withTag("ZenithAPI")
                .d { "HdRezkaParser: Searching for $animeName ($russianName)" }
            val sources = mutableListOf<VideoSource>()
            var hasPartialFailures = false
            try {
                val searchQueries = mutableListOf<String>()
                if (animeName.isNotBlank()) searchQueries.add(animeName)
                russianName?.let { searchQueries.add(it) }

                val cleanEn = AnimeTitleMatcher.cleanForSearch(animeName)
                if (cleanEn != animeName && cleanEn.isNotBlank()) searchQueries.add(cleanEn)
                val baseEn = animeName.substringBefore(":").substringBefore("-").trim()
                if (baseEn.length > 3 && baseEn != animeName) searchQueries.add(baseEn)

                russianName?.let {
                    val cleanRu = AnimeTitleMatcher.cleanForSearch(it)
                    if (cleanRu != it && cleanRu.isNotBlank()) searchQueries.add(cleanRu)
                    val baseRu = it.substringBefore(":").substringBefore("-").trim()
                    if (baseRu.length > 3 && baseRu != it) searchQueries.add(baseRu)
                }

                val queries = searchQueries.distinct()
                var pageHtml: String? = null

                for (query in queries) {
                    try {
                        val searchHtml = hdRezkaApi.search(query)
                        val doc = Ksoup.parse(searchHtml)
                        val items = doc.select(".b-content__inline_item")
                        if (items.isNotEmpty()) {
                            val targetSubRu = russianName
                                ?.substringAfter(":", "")
                                ?.substringAfter("-", "")
                                ?.trim()
                                ?.lowercase()
                            val targetSubEn = animeName
                                .substringAfter(":", "")
                                .substringAfter("-", "")
                                .trim()
                                .lowercase()

                            val matchedItem = items.firstOrNull { item ->
                                val titleText = item.selectFirst(".b-content__inline_item-link")?.text()?.lowercase() ?: ""
                                val itemUrl = item.attr("data-url").lowercase()
                                (targetSubRu?.isNotBlank() == true && (titleText.contains(targetSubRu) || itemUrl.contains(targetSubRu))) ||
                                    (targetSubEn.isNotBlank() && (titleText.contains(targetSubEn) || itemUrl.contains(targetSubEn))) ||
                                    ((targetSubEn == "piece" || targetSubRu == "кусочек") && (titleText.contains("фрагмент") || itemUrl.contains("fragment")))
                            } ?: items.first()

                            val link = matchedItem?.selectFirst("a")?.attr("href")
                            if (link != null) {
                                co.touchlab.kermit.Logger
                                    .withTag("ZenithAPI")
                                    .d { "HDRezka: Found link: $link" }
                                pageHtml = hdRezkaApi.getPage(link)
                                break
                            }
                        } else if (doc.selectFirst("#post_id") != null) {
                            pageHtml = searchHtml
                            break
                        }
                    } catch (e: Exception) {
                        co.touchlab.kermit.Logger
                            .withTag("ZenithAPI")
                            .e(e) { "HDRezka Search Error" }
                    }
                }

                if (pageHtml == null) return ParserResult(sources, false)

                val doc = Ksoup.parse(pageHtml)
                val postId = doc.selectFirst("#post_id")?.attr("value") ?: return ParserResult(sources, false)
                val isSeries = doc.html().contains("initCDNSeriesEvents") || doc.selectFirst(".b-simple_episode__item") != null

                val translators = mutableMapOf<String, String>()
                val translatorElements = doc.select("#translators-list li")

                if (translatorElements.isNotEmpty()) {
                    translatorElements.forEach { el ->
                        val tId = el.attr("data-translator_id")
                        val tName = el.text().trim()
                        if (tId.isNotEmpty()) translators[tId] = tName
                    }
                } else {
                    val isCamrip = doc.selectFirst("#camrip_video_warning") != null
                    if (!isCamrip) {
                        translators["default"] = "По умолчанию"
                    }
                }

                val activeTranslatorId = doc.selectFirst("#translators-list li.active")?.attr("data-translator_id") ?: ""

                for ((tId, tName) in translators) {
                    try {
                        val actualTId = if (tId == "default") "" else tId
                        val tType =
                            when {
                                tName.contains("Sub", ignoreCase = true) || tName.contains("Субтитры", ignoreCase = true) -> TranslationType.SUB
                                tName.contains("Dub", ignoreCase = true) || tName.contains("Дубляж", ignoreCase = true) -> TranslationType.DUB
                                else -> TranslationType.VO
                            }

                        if (isSeries) {
                            val episodesHtml =
                                if (actualTId == activeTranslatorId || translators.size == 1) {
                                    doc.select("#simple-episodes-list-$actualTId").html().ifEmpty {
                                        doc.selectFirst(".b-simple_episode__item")?.parent()?.html()
                                    } ?: ""
                                } else {
                                    val epResponse = hdRezkaApi.getEpisodes(postId, actualTId)
                                    val obj =
                                        try {
                                            json.decodeFromString<JsonObject>(epResponse)
                                        } catch (e: Exception) {
                                            null
                                        }
                                    obj?.get("episodes")?.jsonPrimitive?.content ?: ""
                                }

                            if (episodesHtml.isNotBlank()) {
                                val epDoc = Ksoup.parse(episodesHtml)
                                val epItems = epDoc.select(".b-simple_episode__item")
                                val episodes = mutableListOf<EpisodeSource>()

                                epItems.forEach { item ->
                                    val sId = item.attr("data-season_id")
                                    val eId = item.attr("data-episode_id")
                                    val eNum = item.attr("data-episode_id").toIntOrNull() ?: item.text().filter { it.isDigit() }.toIntOrNull() ?: 1
                                    if (sId.isNotBlank() && eId.isNotBlank()) {
                                        val url = "hdrezka://$postId/${actualTId.ifEmpty { "default" }}/$sId/$eId"
                                        episodes.add(EpisodeSource(eNum, url))
                                    }
                                }

                                if (episodes.isNotEmpty()) {
                                    sources.add(
                                        VideoSource(
                                            name = "HDRezka",
                                            translationName = tName,
                                            translationType = tType,
                                            providerId = BuiltInProviders.HDREZKA,
                                            episodes = episodes,
                                        ),
                                    )
                                }
                            }
                        } else {
                            val streamsResponse = hdRezkaApi.getStreamLinks(postId, actualTId)
                            val obj =
                                try {
                                    json.decodeFromString<JsonObject>(streamsResponse)
                                } catch (e: Exception) {
                                    null
                                }
                            val urlStr = obj?.get("url")?.jsonPrimitive?.content ?: ""

                            if (urlStr.isNotBlank()) {
                                val decoded = HdRezkaDecoder.clearTrash(urlStr)
                                val qualitiesMap = HdRezkaDecoder.parseQualities(decoded)
                                val bestQuality = HdRezkaDecoder.getBestQuality(qualitiesMap)
                                val bestUrl = qualitiesMap[bestQuality] ?: ""

                                if (bestUrl.isNotBlank()) {
                                    sources.add(
                                        VideoSource(
                                            name = "HDRezka",
                                            translationName = tName,
                                            translationType = tType,
                                            providerId = BuiltInProviders.HDREZKA,
                                            episodes = listOf(EpisodeSource(1, bestUrl, qualitiesMap)),
                                            qualities = qualitiesMap,
                                        ),
                                    )
                                }
                            }
                        }
                    } catch (e: Exception) {
                        hasPartialFailures = true
                        co.touchlab.kermit.Logger
                            .withTag("ZenithAPI")
                            .e(e) { "HDRezka Extraction Error for translator $tId" }
                    }
                }
            } catch (e: Exception) {
                co.touchlab.kermit.Logger
                    .withTag("ZenithAPI")
                    .e(e) { "HdRezkaParser error" }
                return ParserResult(sources, true)
            }
            return ParserResult(sources, hasPartialFailures)
        }

        override suspend fun getHdRezkaSources(
            shikimoriId: Int,
            animeName: String,
            russianName: String?,
        ): List<VideoSource> = getSources(shikimoriId, animeName, russianName).sources

        override suspend fun resolve(url: String): HdRezkaStream? = resolveInternal(url, null)

        override suspend fun resolveForMirror(
            url: String,
            mirror: String,
        ): HdRezkaStream? = resolveInternal(url, mirror)

        private val logger = co.touchlab.kermit.Logger
            .withTag("ZenithDiagnostic")

        private suspend fun resolveInternal(
            url: String,
            mirror: String?,
        ): HdRezkaStream? {
            if (!url.startsWith("hdrezka://")) return null
            val parts = url.removePrefix("hdrezka://").split("/")
            if (parts.size < 2) return null
            val postId = parts[0]
            val translatorId = if (parts[1] == "default") "" else parts[1]
            val season = if (parts.size >= 4) parts[2] else null
            val episode = if (parts.size >= 4) parts[3] else null

            return try {
                val response = if (mirror != null) {
                    hdRezkaApi.getStreamLinksForMirror(mirror, postId, translatorId, season, episode)
                } else {
                    hdRezkaApi.getStreamLinks(postId, translatorId, season, episode)
                }
                logger.d { "[$mirror] resolveInternal raw response: ${response.take(200)}" }
                val obj = try {
                    json.decodeFromString<JsonObject>(response)
                } catch (e: Exception) {
                    logger.d { "[$mirror] resolveInternal JSON parse failed: ${e.message}" }
                    null
                }
                if (obj?.get("success")?.jsonPrimitive?.content == "false") {
                    val msg = obj.get("message")?.jsonPrimitive?.content
                    logger.d { "[$mirror] resolveInternal returned success=false: $msg" }
                    return null
                }

                val urlStr = obj?.get("url")?.jsonPrimitive?.content ?: ""
                if (urlStr.isNotBlank()) {
                    val decoded = HdRezkaDecoder.clearTrash(urlStr)
                    val qualitiesMap = HdRezkaDecoder.parseQualities(decoded)
                    val bestQuality = HdRezkaDecoder.getBestQuality(qualitiesMap)
                    qualitiesMap[bestQuality]?.let { return HdRezkaStream(bestQuality, it, qualitiesMap) }
                }
                null
            } catch (e: Exception) {
                logger.d { "Failed to resolve HDRezka URL on mirror $mirror: ${e.message}" }
                null
            }
        }

        override suspend fun loginToMirror(
            mirror: String,
            login: String,
            password: String
        ): Boolean = hdRezkaApi.loginToMirror(mirror, login, password)

        override suspend fun verifyStreamUrl(
            url: String,
            mirror: String
        ): Boolean = hdRezkaApi.verifyStreamUrl(url, mirror)
    }
