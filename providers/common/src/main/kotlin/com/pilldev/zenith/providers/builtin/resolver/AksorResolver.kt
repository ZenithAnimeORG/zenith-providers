package com.pilldev.zenith.providers.builtin.resolver

import com.fleeksoft.ksoup.Ksoup
import com.pilldev.zenith.providers.builtin.model.ResolvedMedia
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock

@Serializable
internal data class AksorApiResponse(
    val id: String? = null,
    @SerialName("anime_id") val animeId: Int? = null,
    val episode: String? = null,
    @SerialName("studio_name") val studioName: String? = null,
    val qualities: Map<String, String?>? = null,
)

open class AksorResolver(
    private val httpClient: HttpClient,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    },
) {
    private val cache = mutableMapOf<String, Pair<ResolvedMedia, Long>>()
    private val defaultUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
    private val videoIdRegex = Regex("""/(?:video|api/video)/([a-zA-Z0-9_-]+)""")

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

        val apiResult = resolveFromApi(url)
        if (apiResult != null) {
            cache[url] = apiResult to now
            return apiResult
        }

        val direct = resolveDirect(url, referer)
        if (direct != null) {
            cache[url] = direct to now
            return direct
        }

        return null
    }

    private suspend fun resolveFromApi(url: String): ResolvedMedia? =
        runCatching {
            val videoId = videoIdRegex.find(url)?.groupValues?.getOrNull(1) ?: return@runCatching null
            val apiUrl = "https://player.aksor.tv/api/video/$videoId"
            val response = httpClient.get(apiUrl) {
                headers {
                    set(HttpHeaders.UserAgent, defaultUserAgent)
                    set(HttpHeaders.Referrer, "https://player.aksor.tv/video/$videoId")
                    set(HttpHeaders.Accept, "application/json, text/plain, */*")
                }
            }
            val text = response.bodyAsText()
            val apiData = json.decodeFromString<AksorApiResponse>(text)
            val qualitiesMap = mutableMapOf<String, String>()

            apiData.qualities?.forEach { (key, streamUrl) ->
                if (!streamUrl.isNullOrBlank()) {
                    val label = when (key) {
                        "q1080" -> "1080p"
                        "q720" -> "720p"
                        "q480" -> "480p"
                        "q360" -> "360p"
                        "q2k" -> "2K"
                        "q4k" -> "4K"
                        else -> key.removePrefix("q")
                    }
                    qualitiesMap[label] = streamUrl
                }
            }

            if (qualitiesMap.isEmpty()) return@runCatching null

            val primaryUrl = qualitiesMap["1080p"]
                ?: qualitiesMap["720p"]
                ?: qualitiesMap["480p"]
                ?: qualitiesMap["360p"]
                ?: qualitiesMap.values.first()

            val cleanQualities = qualitiesMap.toMutableMap()
            if (!cleanQualities.containsKey("auto")) {
                cleanQualities["auto"] = primaryUrl
            }

            ResolvedMedia(
                url = primaryUrl,
                qualities = cleanQualities,
                headers = mapOf(
                    "Referer" to "https://player.aksor.tv/",
                    "User-Agent" to defaultUserAgent,
                ),
            )
        }.getOrNull()

    private suspend fun resolveDirect(
        url: String,
        referer: String?
    ): ResolvedMedia? =
        runCatching {
            val targetUrl = if (url.startsWith("//")) "https:$url" else url
            val effectiveReferer = referer ?: "https://yummyanime.tv/"
            val response =
                httpClient.get(targetUrl) {
                    headers {
                        set(HttpHeaders.UserAgent, defaultUserAgent)
                        set(HttpHeaders.Referrer, effectiveReferer)
                        set(HttpHeaders.Accept, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    }
                }
            val html = response.bodyAsText()
            val doc = Ksoup.parse(html)
            val videoMeta = doc.selectFirst("meta[name=video_url]")?.attr("content")
            if (!videoMeta.isNullOrBlank() && !videoMeta.contains("{{") && (videoMeta.startsWith("http://") || videoMeta.startsWith("https://") || videoMeta.startsWith("//"))) {
                val fullVideoUrl = if (videoMeta.startsWith("//")) "https:$videoMeta" else videoMeta
                return@runCatching ResolvedMedia(
                    url = fullVideoUrl,
                    qualities = mapOf("auto" to fullVideoUrl),
                    headers =
                        mapOf(
                            "Referer" to "https://player.aksor.tv/",
                            "User-Agent" to defaultUserAgent,
                        ),
                )
            }

            val videoSrc = doc.selectFirst("video source")?.attr("src") ?: doc.selectFirst("video")?.attr("src")
            if (!videoSrc.isNullOrBlank()) {
                val fullVideoUrl = if (videoSrc.startsWith("//")) "https:$videoSrc" else videoSrc
                return@runCatching ResolvedMedia(
                    url = fullVideoUrl,
                    qualities = mapOf("auto" to fullVideoUrl),
                    headers =
                        mapOf(
                            "Referer" to "https://player.aksor.tv/",
                            "User-Agent" to defaultUserAgent,
                        ),
                )
            }

            null
        }.getOrNull()
}
