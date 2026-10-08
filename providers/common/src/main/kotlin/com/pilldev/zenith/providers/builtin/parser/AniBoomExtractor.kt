package com.pilldev.zenith.providers.builtin.parser

import co.touchlab.kermit.Logger
import com.fleeksoft.ksoup.Ksoup
import com.pilldev.zenith.provider.model.ProviderMediaStream
import com.pilldev.zenith.providers.builtin.parser.AnimeGoMirrorSelector
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

open class AniBoomExtractor
    constructor(
        private val httpClient: HttpClient,
        private val json: Json,
    ) {
        private val logger = Logger.withTag("AniBoomExtractor")

        public suspend fun extractStream(aniboomUrl: String): List<ProviderMediaStream> {
            val response =
                httpClient.get(aniboomUrl) {
                    header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    header("User-Agent", AnimeGoMirrorSelector.DEFAULT_USER_AGENT)
                    header("Referer", "https://animego.me/")
                }

            if (response.status != HttpStatusCode.OK) return emptyList()
            val html = response.bodyAsText()
            return parseParameters(html)
        }

        public fun parseParameters(html: String): List<ProviderMediaStream> {
            val doc = Ksoup.parse(html)
            val video = doc.selectFirst("video[data-parameters]")
            val rawParams = video?.attr("data-parameters")

            val paramsString =
                if (!rawParams.isNullOrBlank()) {
                    rawParams
                } else {
                    Regex("data-parameters=\"([^\"]+)\"").find(html)?.groupValues?.get(1) ?: return emptyList()
                }

            val streams = mutableListOf<ProviderMediaStream>()

            try {
                val paramsJson = json.parseToJsonElement(paramsString).jsonObject
                val hlsSrc = extractSrc(paramsJson["hls"])

                if (!hlsSrc.isNullOrBlank()) {
                    streams.add(
                        ProviderMediaStream(
                            url = hlsSrc,
                            quality = "1080p",
                            headers =
                                mapOf(
                                    "Referer" to "https://aniboom.one/",
                                    "Origin" to "https://aniboom.one",
                                    "User-Agent" to AnimeGoMirrorSelector.DEFAULT_USER_AGENT,
                                ),
                            isHls = true,
                        ),
                    )
                }

                val dashSrc = extractSrc(paramsJson["dash"])
                if (!dashSrc.isNullOrBlank() && streams.isEmpty()) {
                    streams.add(
                        ProviderMediaStream(
                            url = dashSrc,
                            quality = "DASH",
                            headers =
                                mapOf(
                                    "Referer" to "https://aniboom.one/",
                                    "Origin" to "https://aniboom.one",
                                    "User-Agent" to AnimeGoMirrorSelector.DEFAULT_USER_AGENT,
                                ),
                            isHls = false,
                        ),
                    )
                }
            } catch (e: Exception) {
                logger.w(e) { "Failed to parse AniBoom JSON parameters" }
            }

            return streams
        }

        private fun extractSrc(element: kotlinx.serialization.json.JsonElement?): String? {
            if (element == null) return null
            return when (element) {
                is JsonObject -> element["src"]?.jsonPrimitive?.content
                else -> {
                    val innerStr = element.jsonPrimitive.content
                    if (innerStr.startsWith("{")) {
                        try {
                            json
                                .parseToJsonElement(innerStr)
                                .jsonObject["src"]
                                ?.jsonPrimitive
                                ?.content
                        } catch (_: Exception) {
                            null
                        }
                    } else {
                        innerStr.takeIf { it.startsWith("http") }
                    }
                }
            }
        }
    }
