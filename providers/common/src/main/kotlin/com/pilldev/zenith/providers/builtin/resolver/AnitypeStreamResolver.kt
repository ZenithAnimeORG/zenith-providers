package com.pilldev.zenith.providers.builtin.resolver

import com.pilldev.zenith.domain.repository.PlayerSettingsRepository
import com.pilldev.zenith.providers.builtin.model.ResolvedMedia
import com.pilldev.zenith.providers.builtin.resolver.KodikResolver
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.encodeURLQueryComponent
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val INDEX_SHIKIMORI = 2
private const val INDEX_EPISODE = 3
private const val QUALITY_1080 = 1080
private const val QUALITY_720 = 720

@Serializable
private data class AnitypeVideoSourceDto(
    val quality: Int,
    val src: String,
    val only_sub: Boolean = false,
)

open class AnitypeStreamResolver
    constructor(
        private val aniTypeHttpClient: io.ktor.client.HttpClient,
        private val playerSettingsManager: PlayerSettingsRepository,
        private val kodikResolver: KodikResolver,
        private val json: Json,
    ) {
        open suspend fun resolveAnitypeUrl(url: String): ResolvedMedia? {
            val parsedUri = Url(url)
            val pathParts = parsedUri.encodedPath.split("/")
            val shikimoriId = pathParts.getOrNull(INDEX_SHIKIMORI)?.toIntOrNull()
            val epNum = pathParts.getOrNull(INDEX_EPISODE)?.toIntOrNull()
            val translation = parsedUri.parameters["translation"] ?: ""
            val kodikUrl = parsedUri.parameters["kodik"] ?: ""

            val hasToken = playerSettingsManager.anitypeAccessToken.value.isNotBlank()
            if (shikimoriId != null && epNum != null && hasToken) {
                runCatching {
                    val additionalUrl = "https://anitype.site/kodik/additional/$shikimoriId/$epNum?translation=${translation.encodeURLQueryComponent()}"
                    val apiResponse =
                        aniTypeHttpClient.get(additionalUrl) {
                            header("Origin", "https://player.wwwanitype.fun")
                            header("Referer", "https://player.wwwanitype.fun/")
                        }
                    if (apiResponse.status == HttpStatusCode.OK) {
                        val dtoList = json.decodeFromString<List<AnitypeVideoSourceDto>>(apiResponse.bodyAsText())
                        val qualities = mutableMapOf<String, String>()
                        var primaryUrl = ""
                        dtoList.forEach { item ->
                            if (item.src != "only_sub" && !item.only_sub) {
                                val qStr = "${item.quality}p"
                                qualities[qStr] = item.src
                                val isPreferred = item.quality == QUALITY_1080 || item.quality == QUALITY_720
                                if (primaryUrl.isEmpty() || isPreferred) {
                                    primaryUrl = item.src
                                }
                            }
                        }
                        if (primaryUrl.isEmpty() && qualities.isNotEmpty()) {
                            primaryUrl = qualities.values.first()
                        }
                        if (primaryUrl.isNotEmpty()) {
                            return ResolvedMedia(primaryUrl, qualities, emptyMap())
                        }
                    }
                }
            }

            if (kodikUrl.isNotBlank()) {
                kodikResolver.resolve(kodikUrl, referer = "https://wwwanitype.fun/")?.let {
                    return ResolvedMedia(it.url, it.qualities, it.headers)
                }
            }
            return null
        }
    }
