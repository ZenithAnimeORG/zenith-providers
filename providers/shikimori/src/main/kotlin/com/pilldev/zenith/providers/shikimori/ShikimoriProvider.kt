package com.pilldev.zenith.providers.shikimori

import com.pilldev.zenith.provider.BaseZenithProvider
import com.pilldev.zenith.provider.CatalogMediaDetails
import com.pilldev.zenith.provider.CatalogMediaItem
import com.pilldev.zenith.provider.CatalogProvider
import com.pilldev.zenith.provider.CatalogSearchResult
import com.pilldev.zenith.provider.CatalogSection
import com.pilldev.zenith.provider.PosterSourceProvider
import com.pilldev.zenith.provider.TrackerProvider
import com.pilldev.zenith.provider.context.ProviderContext
import com.pilldev.zenith.provider.model.MediaId
import com.pilldev.zenith.provider.model.MediaRef
import com.pilldev.zenith.provider.model.PluginManifest
import com.pilldev.zenith.provider.model.ProviderId
import com.pilldev.zenith.provider.model.ProviderMetadata
import com.pilldev.zenith.provider.model.ProviderResult
import com.pilldev.zenith.provider.model.ProviderTestResult
import com.pilldev.zenith.provider.model.UserMediaStatus
import com.pilldev.zenith.provider.model.measureTest
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

public class ShikimoriProvider(
    manifest: PluginManifest,
    private val injectedClient: HttpClient? = null,
) : BaseZenithProvider(), CatalogProvider, TrackerProvider, PosterSourceProvider {

    override val metadata: ProviderMetadata = manifest

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private fun client(): HttpClient = injectedClient ?: context?.httpClient ?: HttpClient()

    private fun baseUrl(): String {
        val configured = context?.getSetting("base_url")?.trim().orEmpty()
        return if (configured.startsWith("http")) configured else "https://shikimori.one"
    }

    private fun accessToken(): String? =
        context?.getSetting("access_token")?.trim()?.ifBlank { null }

    override suspend fun test(context: ProviderContext): ProviderTestResult = measureTest {
        val result = search("Horimiya", page = 1, pageSize = 1)
        when (result) {
            is ProviderResult.Success -> "Shikimori API активен. Найдено результатов: ${result.data.items.size}"
            is ProviderResult.Failure -> error("Ошибка доступа к Shikimori API: ${result.message}")
        }
    }

    override suspend fun search(
        query: String,
        page: Int,
        pageSize: Int,
    ): ProviderResult<CatalogSearchResult> = ProviderResult.of {
        val cleanQuery = query.trim()
        val graphQuery = if (cleanQuery.isBlank()) {
            """
            {
              animes(page: $page, limit: $pageSize, order: popularity) {
                id malId name russian score episodes airedOn { year } poster { originalUrl previewUrl }
              }
            }
            """.trimIndent()
        } else {
            """
            {
              animes(search: "$cleanQuery", page: $page, limit: $pageSize, order: popularity) {
                id malId name russian score episodes airedOn { year } poster { originalUrl previewUrl }
              }
            }
            """.trimIndent()
        }

        val jsonResponse = executeGraphQL(graphQuery)
        val animes = jsonResponse["data"]?.jsonObject?.get("animes")?.jsonArray ?: emptyList()
        val items = animes.mapNotNull { parseMediaItem(it.jsonObject) }
        CatalogSearchResult(items = items, hasNextPage = items.size >= pageSize)
    }

    override suspend fun getDetails(mediaRef: MediaRef): ProviderResult<CatalogMediaDetails> = ProviderResult.of {
        val targetId = mediaRef.externalIds[ProviderId("shikimori")]
            ?: mediaRef.id.value.substringAfter(":").ifBlank { mediaRef.id.value }

        val graphQuery = """
        {
          animes(ids: "$targetId") {
            id malId name russian kind status score episodes episodesAired rating description
            poster { originalUrl previewUrl }
            genres { name russian }
            studios { name }
          }
        }
        """.trimIndent()

        val jsonResponse = executeGraphQL(graphQuery)
        val animeObj = jsonResponse["data"]?.jsonObject?.get("animes")?.jsonArray?.firstOrNull()?.jsonObject
            ?: error("Anime not found for id: $targetId")

        parseMediaDetails(animeObj)
    }

    override suspend fun getHomeSections(): ProviderResult<List<CatalogSection>> = ProviderResult.of {
        val graphQuery = """
        {
          trending: animes(order: popularity, status: "ongoing", limit: 20) {
            id malId name russian score episodes airedOn { year } poster { originalUrl }
          }
          popular: animes(order: popularity, limit: 20) {
            id malId name russian score episodes airedOn { year } poster { originalUrl }
          }
          latest: animes(order: aired_on, limit: 20) {
            id malId name russian score episodes airedOn { year } poster { originalUrl }
          }
        }
        """.trimIndent()

        val jsonResponse = executeGraphQL(graphQuery)
        val data = jsonResponse["data"]?.jsonObject

        val trending = data?.get("trending")?.jsonArray?.mapNotNull { parseMediaItem(it.jsonObject) }.orEmpty()
        val popular = data?.get("popular")?.jsonArray?.mapNotNull { parseMediaItem(it.jsonObject) }.orEmpty()
        val latest = data?.get("latest")?.jsonArray?.mapNotNull { parseMediaItem(it.jsonObject) }.orEmpty()

        listOf(
            CatalogSection(title = "В тренде", items = trending),
            CatalogSection(title = "Популярное", items = popular),
            CatalogSection(title = "Новинки", items = latest),
        )
    }

    override suspend fun getRelated(mediaRef: MediaRef): ProviderResult<List<CatalogMediaItem>> = ProviderResult.of {
        val targetId = mediaRef.externalIds[ProviderId("shikimori")]
            ?: mediaRef.id.value.substringAfter(":").ifBlank { mediaRef.id.value }

        val graphQuery = """
        {
          animes(ids: "$targetId") {
            related {
              anime {
                id malId name russian score episodes airedOn { year } poster { originalUrl }
              }
            }
          }
        }
        """.trimIndent()

        val jsonResponse = executeGraphQL(graphQuery)
        val relatedArray = jsonResponse["data"]
            ?.jsonObject
            ?.get("animes")
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("related")
            ?.jsonArray

        relatedArray?.mapNotNull { edge ->
            edge.jsonObject["anime"]?.jsonObject?.let { parseMediaItem(it) }
        }.orEmpty()
    }

    override suspend fun onEpisodeWatched(mediaRef: MediaRef, episode: Int): ProviderResult<Unit> = ProviderResult.of {
        val token = accessToken() ?: return@of
        val targetId = mediaRef.externalIds[ProviderId("shikimori")]?.toIntOrNull()
            ?: mediaRef.id.value.substringAfter(":").toIntOrNull() ?: return@of

        val url = "${baseUrl()}/api/v2/user_rates"
        val body = buildJsonObject {
            put("user_rate", buildJsonObject {
                put("target_id", targetId)
                put("target_type", "Anime")
                put("episodes", episode)
            })
        }

        client().post(url) {
            header("Authorization", "Bearer $token")
            header("User-Agent", "Zenith-App/2.0")
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
        Unit
    }

    override suspend fun syncStatus(mediaRef: MediaRef, status: UserMediaStatus): ProviderResult<Unit> = ProviderResult.of {
        val token = accessToken() ?: return@of
        val targetId = mediaRef.externalIds[ProviderId("shikimori")]?.toIntOrNull()
            ?: mediaRef.id.value.substringAfter(":").toIntOrNull() ?: return@of

        val shikiStatus = when (status) {
            UserMediaStatus.WATCHING -> "watching"
            UserMediaStatus.PLANNED -> "planned"
            UserMediaStatus.COMPLETED -> "completed"
            UserMediaStatus.ON_HOLD -> "on_hold"
            UserMediaStatus.DROPPED -> "dropped"
        }

        val url = "${baseUrl()}/api/v2/user_rates"
        val body = buildJsonObject {
            put("user_rate", buildJsonObject {
                put("target_id", targetId)
                put("target_type", "Anime")
                put("status", shikiStatus)
            })
        }

        client().post(url) {
            header("Authorization", "Bearer $token")
            header("User-Agent", "Zenith-App/2.0")
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
        Unit
    }

    override suspend fun getPoster(animeId: Int, animeName: String): ProviderResult<String?> = ProviderResult.of {
        val result = getDetails(MediaRef(MediaId("shikimori:$animeId"), mapOf(ProviderId("shikimori") to animeId.toString())))
        (result as? ProviderResult.Success)?.data?.posterUrl
    }

    private suspend fun executeGraphQL(query: String): JsonObject {
        val url = "${baseUrl()}/api/graphql"
        val requestBody = buildJsonObject { put("query", query) }
        val responseText = client().post(url) {
            header("User-Agent", "Zenith-App/2.0")
            accessToken()?.let { header("Authorization", "Bearer $it") }
            contentType(ContentType.Application.Json)
            setBody(requestBody.toString())
        }.bodyAsText()

        val parsed = json.decodeFromString<JsonObject>(responseText)
        if ("errors" in parsed) {
            val errorMsg = parsed["errors"].toString()
            error("Shikimori GraphQL error: $errorMsg")
        }
        return parsed
    }

    private fun parseMediaItem(obj: JsonObject): CatalogMediaItem? {
        val id = obj["id"].asString() ?: return null
        val malId = obj["malId"].asString() ?: id
        val name = obj["name"].asString() ?: ""
        val russian = obj["russian"].asString()
        val score = obj["score"]?.jsonPrimitive?.doubleOrNull
        val episodes = obj["episodes"]?.jsonPrimitive?.intOrNull
        val year = obj["airedOn"]?.jsonObject?.get("year")?.jsonPrimitive?.intOrNull
        val rawPoster = obj["poster"]?.jsonObject?.get("originalUrl").asString()
        val poster = rawPoster?.let { if (it.startsWith("http")) it else "${baseUrl()}$it" }

        return CatalogMediaItem(
            mediaRef = MediaRef(
                id = MediaId("shikimori:$id"),
                externalIds = mapOf(ProviderId("shikimori") to id, ProviderId("mal") to malId),
            ),
            title = russian ?: name,
            originalTitle = name,
            posterUrl = poster,
            score = score,
            year = year,
            totalEpisodes = episodes,
        )
    }

    private fun parseMediaDetails(obj: JsonObject): CatalogMediaDetails {
        val id = obj["id"].asString() ?: ""
        val malId = obj["malId"].asString() ?: id
        val name = obj["name"].asString() ?: ""
        val russian = obj["russian"].asString()
        val description = obj["description"].asString()
        val score = obj["score"]?.jsonPrimitive?.doubleOrNull
        val episodes = obj["episodes"]?.jsonPrimitive?.intOrNull
        val rawPoster = obj["poster"]?.jsonObject?.get("originalUrl").asString()
        val poster = rawPoster?.let { if (it.startsWith("http")) it else "${baseUrl()}$it" }
        val status = obj["status"].asString()

        val genres = obj["genres"]?.jsonArray?.mapNotNull {
            val g = it.jsonObject
            g["russian"].asString() ?: g["name"].asString()
        }.orEmpty()

        val studios = obj["studios"]?.jsonArray?.mapNotNull {
            it.jsonObject["name"].asString()
        }.orEmpty()

        return CatalogMediaDetails(
            mediaRef = MediaRef(
                id = MediaId("shikimori:$id"),
                externalIds = mapOf(ProviderId("shikimori") to id, ProviderId("mal") to malId),
            ),
            title = russian ?: name,
            originalTitle = name,
            description = description,
            posterUrl = poster,
            bannerUrl = poster,
            score = score,
            year = null,
            episodesCount = episodes,
            genres = genres,
            studios = studios,
            status = status,
        )
    }

    private fun JsonElement?.asString(): String? {
        if (this == null || this is JsonNull) return null
        return runCatching { this.jsonPrimitive.content }.getOrNull()
    }
}
