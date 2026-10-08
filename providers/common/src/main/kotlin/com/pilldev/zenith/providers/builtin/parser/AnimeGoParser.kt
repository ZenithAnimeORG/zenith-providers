package com.pilldev.zenith.providers.builtin.parser

import co.touchlab.kermit.Logger
import com.fleeksoft.ksoup.Ksoup
import com.pilldev.zenith.domain.model.AnimeTitleMatcher
import com.pilldev.zenith.domain.model.EpisodeSource
import com.pilldev.zenith.domain.model.TranslationType
import com.pilldev.zenith.domain.model.VideoSource
import com.pilldev.zenith.domain.repository.AppDispatchers
import com.pilldev.zenith.domain.repository.ProviderSettingsRepository
import com.pilldev.zenith.provider.model.BuiltInProviders
import com.pilldev.zenith.provider.model.ProviderMediaStream
import com.pilldev.zenith.providers.builtin.parser.AnimeGoMirrorSelector
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

open class AnimeGoParser
    constructor(
        private val httpClient: HttpClient,
        private val json: Json,
        private val appDispatchers: AppDispatchers,
        public val aniBoomExtractor: AniBoomExtractor,
        private val providerSettingsRepository: ProviderSettingsRepository? = null,
        private val mirrorRepository: com.pilldev.zenith.domain.repository.MirrorRepository? = null,
    ) : AnimeParser {
        override val name: String = "AnimeGO"

        private val logger = Logger.withTag("AnimeGoParser")
        private val cleanRegex = Regex("(?i)\\s*(Season|2nd Season|Part|TV|Vost|AniLibria).*|[:!]")

        override suspend fun getSources(
            shikimoriId: Int,
            animeName: String,
            russianName: String?,
        ): ParserResult =
            withContext(appDispatchers.io) {
                logger.d { "AnimeGoParser: Searching for $animeName ($russianName) [Shikimori ID: $shikimoriId]" }
                try {
                    val mirror = resolveBaseUrl()
                    val animeUrl = findAnimePageUrl(mirror, animeName, russianName)
                        ?: return@withContext ParserResult(emptyList())

                    val playerContent = fetchPlayerContent(mirror, animeUrl)
                    if (playerContent.isNullOrBlank()) return@withContext ParserResult(emptyList())

                    ParserResult(parsePlayerContent(playerContent))
                } catch (e: Exception) {
                    logger.e(e) { "AnimeGoParser: Failed to fetch sources for $animeName" }
                    ParserResult(emptyList(), hasPartialFailures = true)
                }
            }

        private suspend fun resolveBaseUrl(): String {
            if (mirrorRepository != null) {
                val mirror = mirrorRepository.getEffectiveMirror("AnimeGO")
                if (mirror.isNotBlank()) return AnimeGoMirrorSelector.normalizeMirrorUrl(mirror)
            }
            val custom = providerSettingsRepository?.getSetting(BuiltInProviders.ANIMEGO, "base_url")
            return AnimeGoMirrorSelector.selectBestMirror(httpClient, custom)
        }

        public suspend fun findAnimePageUrl(
            baseUrl: String,
            animeName: String,
            russianName: String?,
        ): String? {
            val queries = buildSearchQueries(animeName, russianName)
            val normalizedBase = AnimeGoMirrorSelector.normalizeMirrorUrl(baseUrl)

            for (query in queries) {
                try {
                    val searchUrl = "${normalizedBase}search/anime?q=${query.encodeURLParameter()}"
                    val response = httpClient.get(searchUrl) {
                        header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        header("User-Agent", AnimeGoMirrorSelector.DEFAULT_USER_AGENT)
                    }
                    if (response.status != HttpStatusCode.OK) continue
                    val candidate = parseSearchHtml(response.bodyAsText(), normalizedBase, animeName, russianName)
                    if (candidate != null) return candidate
                } catch (e: Exception) {
                    logger.w(e) { "AnimeGoParser search failed for query '$query'" }
                }
            }
            return null
        }

        public fun parseSearchHtml(
            html: String,
            baseUrl: String,
            animeName: String,
            russianName: String?,
        ): String? {
            val doc = Ksoup.parse(html)
            val links = doc.select(".ani-grid__item-title a[href^=\"/anime/\"]").ifEmpty {
                doc.select("a[href^=\"/anime/\"]").filter { a ->
                    val href = a.attr("href")
                    !href.contains("/top-250") &&
                        !href.contains("/status/") &&
                        !href.contains("/season/") &&
                        !href.contains("/random")
                }
            }
            if (links.isEmpty()) return null

            val queryTitles = listOfNotNull(animeName, russianName).filter { it.isNotBlank() }
            val best = links.maxByOrNull { element ->
                AnimeTitleMatcher.scoreWithSynonyms(queryTitles, listOf(element.text().trim()))
            } ?: links.first()

            val href = best.attr("href").trim()
            return if (href.startsWith("http://") || href.startsWith("https://")) {
                href
            } else {
                "${baseUrl.trimEnd('/')}/${href.removePrefix("/")}"
            }
        }

        private suspend fun fetchPlayerContent(
            baseUrl: String,
            animePageUrl: String,
        ): String? {
            try {
                val pageResponse = httpClient.get(animePageUrl) {
                    header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    header("User-Agent", AnimeGoMirrorSelector.DEFAULT_USER_AGENT)
                }
                if (pageResponse.status != HttpStatusCode.OK) return null
                val pageDoc = Ksoup.parse(pageResponse.bodyAsText())

                val playerPath = pageDoc
                    .selectFirst("[data-anime-player-loader-url-value]")
                    ?.attr("data-anime-player-loader-url-value")
                    ?: pageDoc
                        .selectFirst("[data-anime-player-anime-id-value]")
                        ?.attr("data-anime-player-anime-id-value")
                        ?.let { "/player/$it" }
                    ?: return null

                val fullPlayerUrl = if (playerPath.startsWith("http://") || playerPath.startsWith("https://")) {
                    playerPath
                } else {
                    "${AnimeGoMirrorSelector.normalizeMirrorUrl(baseUrl).trimEnd('/')}/${playerPath.removePrefix("/")}"
                }

                val playerResponse = httpClient.get(fullPlayerUrl) {
                    header("Accept", "application/json, text/javascript, */*; q=0.01")
                    header("X-Requested-With", "XMLHttpRequest")
                    header("User-Agent", AnimeGoMirrorSelector.DEFAULT_USER_AGENT)
                    header("Referer", animePageUrl)
                }
                if (playerResponse.status != HttpStatusCode.OK) return null
                val rootObj = json.parseToJsonElement(playerResponse.bodyAsText()).jsonObject
                return rootObj["data"]
                    ?.jsonObject
                    ?.get("content")
                    ?.jsonPrimitive
                    ?.content
            } catch (e: Exception) {
                logger.w(e) { "AnimeGoParser: Failed to fetch player content from $animePageUrl" }
                return null
            }
        }

        public fun parsePlayerContent(playerHtml: String): List<VideoSource> {
            val doc = Ksoup.parse(playerHtml)
            val episodeNumbers = doc
                .select("[data-anime-player-episodes-target=\"episode\"], [data-episode-number]")
                .mapNotNull { it.attr("data-episode-number").toIntOrNull() }
                .distinct()
                .sorted()
                .ifEmpty { listOf(1) }

            val sources = mutableListOf<VideoSource>()
            for (btn in doc.select("[data-anime-player-target=\"provider\"]")) {
                val providerTitle = btn.attr("data-provider-title").trim().ifBlank { "AniBoom" }
                val translationTitle = btn.attr("data-translation-title").trim().ifBlank { "Оригинал" }
                val translationId = btn.attr("data-translation-id").trim()
                var rawPlayerUrl = btn.attr("data-player").trim()
                if (rawPlayerUrl.isBlank()) continue
                if (rawPlayerUrl.startsWith("//")) rawPlayerUrl = "https:$rawPlayerUrl"

                val translationType = when {
                    translationTitle.contains("Субтитры", ignoreCase = true) ||
                        translationTitle.contains("Sub", ignoreCase = true) -> TranslationType.SUB
                    translationTitle.contains("Дубляж", ignoreCase = true) ||
                        translationTitle.contains("Студийная", ignoreCase = true) -> TranslationType.DUB
                    else -> TranslationType.VO
                }

                val episodes = if (rawPlayerUrl.contains("aniboom.one/embed/")) {
                    val baseEmbedUrl = rawPlayerUrl.substringBefore("?")
                    episodeNumbers.map { epNum ->
                        EpisodeSource(
                            number = epNum,
                            url = "$baseEmbedUrl?episode=$epNum&translation=$translationId",
                            providerId = BuiltInProviders.ANIMEGO,
                        )
                    }
                } else {
                    episodeNumbers.map { epNum ->
                        EpisodeSource(number = epNum, url = rawPlayerUrl, providerId = BuiltInProviders.ANIMEGO)
                    }
                }

                sources.add(
                    VideoSource(
                        name = "AnimeGO: $translationTitle ($providerTitle)",
                        translationName = translationTitle,
                        translationType = translationType,
                        providerId = BuiltInProviders.ANIMEGO,
                        episodes = episodes,
                    ),
                )
            }
            return sources
        }

        public suspend fun resolveStream(episodeUrl: String): List<ProviderMediaStream> =
            withContext(appDispatchers.io) {
                try {
                    val normalizedUrl = if (episodeUrl.startsWith("//")) "https:$episodeUrl" else episodeUrl
                    if (normalizedUrl.contains("aniboom.one")) {
                        return@withContext aniBoomExtractor.extractStream(normalizedUrl)
                    }
                    listOf(
                        ProviderMediaStream(
                            url = normalizedUrl,
                            quality = "Auto",
                            isHls = normalizedUrl.contains(".m3u8"),
                        ),
                    )
                } catch (e: Exception) {
                    logger.e(e) { "AnimeGoParser: Failed to resolve stream for $episodeUrl" }
                    emptyList()
                }
            }

        private fun buildSearchQueries(
            animeName: String,
            russianName: String?
        ): List<String> {
            val cleanEn = animeName.replace(cleanRegex, "").trim()
            return listOfNotNull(
                russianName?.takeIf { it.isNotBlank() },
                animeName,
                cleanEn.takeIf { it.isNotBlank() },
            ).distinct()
        }
    }
