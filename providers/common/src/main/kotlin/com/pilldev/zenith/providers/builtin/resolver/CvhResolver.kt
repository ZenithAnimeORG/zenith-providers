package com.pilldev.zenith.providers.builtin.resolver

import com.pilldev.zenith.providers.builtin.model.ResolvedMedia
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock

open class CvhResolver(
    private val httpClient: HttpClient,
    private val json: Json,
) {
    private val cache = mutableMapOf<String, Pair<ResolvedMedia, Long>>()
    private val defaultUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    open suspend fun resolve(
        url: String,
        referer: String? = null
    ): ResolvedMedia? {
        val now = Clock.System.now().toEpochMilliseconds()
        cache[url]?.let { (media, timestamp) ->
            if (now - timestamp < 30 * 60 * 1000L) {
                return media
            }
        }

        val direct = resolveDirect(url, referer)
        if (direct != null) {
            cache[url] = direct to now
            return direct
        }

        return null
    }

    private suspend fun resolveDirect(
        url: String,
        referer: String?
    ): ResolvedMedia? =
        runCatching {
            val queryParams = parseQueryParams(url)
            val animeId = queryParams["anime_id"] ?: return null
            val episodeNum = queryParams["episode"]?.toIntOrNull() ?: 1
            val dubbingCode = queryParams["dubbing_code"] ?: queryParams["dubbing"]

            val playlistUrl = "https://plapi.cdnvideohub.com/api/v1/player/sv/playlist?pub=745&id=$animeId&aggr=mali"
            val response =
                httpClient.get(playlistUrl) {
                    headers {
                        set(HttpHeaders.UserAgent, defaultUserAgent)
                        set(HttpHeaders.Referrer, "https://ru.yummyani.me/")
                        set(HttpHeaders.Origin, "https://ru.yummyani.me")
                        set(HttpHeaders.Accept, "application/json, text/plain, */*")
                    }
                }

            if (response.status != HttpStatusCode.OK) {
                return null
            }

            val body = response.bodyAsText()
            if (body.isBlank() || !body.trim().startsWith("{")) return null

            val root = json.decodeFromString<JsonObject>(body)
            val seasons = root["seasons"]?.jsonObject ?: return null

            var vkId: String? = null
            for ((_, seasonElement) in seasons) {
                val seasonObj = seasonElement.jsonObject
                val episodes = seasonObj["episodes"]?.jsonObject ?: continue
                val epObj = episodes[episodeNum.toString()]?.jsonObject ?: episodes.values.firstOrNull()?.jsonObject ?: continue
                val voices = epObj["voices"]?.jsonObject ?: continue

                val voice =
                    if (!dubbingCode.isNullOrBlank()) {
                        voices.entries
                            .find {
                                it.key.contains(dubbingCode, ignoreCase = true) ||
                                    it.value.jsonObject["name"]
                                        ?.jsonPrimitive
                                        ?.content
                                        ?.contains(dubbingCode, ignoreCase = true) == true
                            }?.value
                            ?.jsonObject ?: voices.values.firstOrNull()?.jsonObject
                    } else {
                        voices.values.firstOrNull()?.jsonObject
                    }

                val videos = voice?.get("videos")?.jsonArray
                val firstVideo = videos?.firstOrNull()?.jsonObject
                vkId = firstVideo?.get("vkId")?.jsonPrimitive?.content
                if (vkId != null) break
            }

            if (vkId != null) {
                val videoUrl = "https://plapi.cdnvideohub.com/api/v1/player/sv/video/$vkId"
                val videoResponse =
                    httpClient.get(videoUrl) {
                        headers {
                            set(HttpHeaders.UserAgent, defaultUserAgent)
                            set(HttpHeaders.Referrer, "https://ru.yummyani.me/")
                            set(HttpHeaders.Origin, "https://ru.yummyani.me")
                        }
                    }
                if (videoResponse.status == HttpStatusCode.OK) {
                    val videoBody = videoResponse.bodyAsText()
                    val videoJson = json.decodeFromString<JsonObject>(videoBody)
                    val dataObj = videoJson["data"]?.jsonObject ?: videoJson
                    val hlsUrl =
                        dataObj["hls"]?.jsonPrimitive?.content
                            ?: dataObj["manifestUrl"]?.jsonPrimitive?.content
                            ?: dataObj["url"]?.jsonPrimitive?.content

                    if (!hlsUrl.isNullOrBlank()) {
                        return@runCatching ResolvedMedia(
                            url = hlsUrl,
                            qualities = mapOf("auto" to hlsUrl),
                            headers =
                                mapOf(
                                    "Referer" to "https://ru.yummyani.me/",
                                    "Origin" to "https://ru.yummyani.me",
                                    "User-Agent" to defaultUserAgent,
                                ),
                        )
                    }
                }
            }

            null
        }.getOrNull()

    private fun parseQueryParams(url: String): Map<String, String> {
        val query = url.substringAfter("?", "")
        if (query.isBlank()) return emptyMap()
        return query
            .split("&")
            .mapNotNull {
                val parts = it.split("=", limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else null
            }.toMap()
    }
}
