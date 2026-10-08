package com.pilldev.zenith.providers.anilist

import com.pilldev.zenith.provider.BaseZenithProvider
import com.pilldev.zenith.provider.CatalogMediaDetails
import com.pilldev.zenith.provider.CatalogMediaItem
import com.pilldev.zenith.provider.CatalogProvider
import com.pilldev.zenith.provider.CatalogSearchResult
import com.pilldev.zenith.provider.CatalogSection
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

public class AniListProvider(
    manifest: PluginManifest,
    private val injectedClient: HttpClient? = null,
) : BaseZenithProvider(), CatalogProvider, TrackerProvider {

    override val metadata: ProviderMetadata = manifest

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private fun client(): HttpClient = injectedClient ?: context?.httpClient ?: HttpClient()

    private fun accessToken(): String? =
        context?.getSetting("access_token")?.trim()?.ifBlank { null }

    override suspend fun test(context: ProviderContext): ProviderTestResult = measureTest {
        val result = search("Attack on Titan", page = 1, pageSize = 1)
        when (result) {
            is ProviderResult.Success -> "AniList GraphQL активен. Найдено результатов: ${result.data.items.size}"
            is ProviderResult.Failure -> error("Ошибка доступа к AniList GraphQL: ${result.message}")
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
            query {
              Page(page: $page, perPage: $pageSize) {
                pageInfo { hasNextPage }
                media(type: ANIME, sort: TRENDING_DESC) {
                  id idMal title { romaji english native } coverImage { extraLarge large }
                  averageScore seasonYear startDate { year } episodes status
                }
              }
            }
            """.trimIndent()
        } else {
            val escapedQuery = cleanQuery.replace("\"", "\\\"")
            """
            query {
              Page(page: $page, perPage: $pageSize) {
                pageInfo { hasNextPage }
                media(search: "$escapedQuery", type: ANIME, sort: POPULARITY_DESC) {
                  id idMal title { romaji english native } coverImage { extraLarge large }
                  averageScore seasonYear startDate { year } episodes status
                }
              }
            }
            """.trimIndent()
        }

        val jsonResponse = executeGraphQL(graphQuery)
        val pageObj = jsonResponse["data"]?.jsonObject?.get("Page")?.jsonObject
        val hasNextPage = pageObj?.get("pageInfo")?.jsonObject?.get("hasNextPage").asString()?.toBooleanStrictOrNull() ?: false
        val mediaList = pageObj?.get("media")?.jsonArray ?: emptyList()
        val items = mediaList.mapNotNull { parseMediaItem(it.jsonObject) }

        CatalogSearchResult(items = items, hasNextPage = hasNextPage)
    }

    override suspend fun getDetails(mediaRef: MediaRef): ProviderResult<CatalogMediaDetails> = ProviderResult.of {
        val targetId = mediaRef.externalIds[ProviderId("anilist")]?.toIntOrNull()
            ?: mediaRef.id.value.substringAfter(":").toIntOrNull()
            ?: error("Invalid AniList media ID: ${mediaRef.id.value}")

        val graphQuery = """
        query {
          Media(id: $targetId, type: ANIME) {
            id idMal title { romaji english native } description(asHtml: false)
            coverImage { extraLarge large } bannerImage averageScore seasonYear startDate { year }
            episodes status genres studios(isMain: true) { nodes { name } }
          }
        }
        """.trimIndent()

        val jsonResponse = executeGraphQL(graphQuery)
        val mediaObj = jsonResponse["data"]?.jsonObject?.get("Media")?.jsonObject
            ?: error("Media not found on AniList for ID: $targetId")

        parseMediaDetails(mediaObj)
    }

    override suspend fun getHomeSections(): ProviderResult<List<CatalogSection>> = ProviderResult.of {
        val graphQuery = """
        query {
          trending: Page(page: 1, perPage: 20) {
            media(type: ANIME, sort: TRENDING_DESC) {
              id idMal title { romaji english native } coverImage { extraLarge large } averageScore seasonYear episodes
            }
          }
          popular: Page(page: 1, perPage: 20) {
            media(type: ANIME, sort: POPULARITY_DESC) {
              id idMal title { romaji english native } coverImage { extraLarge large } averageScore seasonYear episodes
            }
          }
          topRated: Page(page: 1, perPage: 20) {
            media(type: ANIME, sort: SCORE_DESC) {
              id idMal title { romaji english native } coverImage { extraLarge large } averageScore seasonYear episodes
            }
          }
        }
        """.trimIndent()

        val jsonResponse = executeGraphQL(graphQuery)
        val data = jsonResponse["data"]?.jsonObject

        val trending = data?.get("trending")?.jsonObject?.get("media")?.jsonArray?.mapNotNull { parseMediaItem(it.jsonObject) }.orEmpty()
        val popular = data?.get("popular")?.jsonObject?.get("media")?.jsonArray?.mapNotNull { parseMediaItem(it.jsonObject) }.orEmpty()
        val topRated = data?.get("topRated")?.jsonObject?.get("media")?.jsonArray?.mapNotNull { parseMediaItem(it.jsonObject) }.orEmpty()

        listOf(
            CatalogSection(title = "В тренде", items = trending),
            CatalogSection(title = "Популярное", items = popular),
            CatalogSection(title = "Высокий рейтинг", items = topRated),
        )
    }

    override suspend fun getRelated(mediaRef: MediaRef): ProviderResult<List<CatalogMediaItem>> = ProviderResult.of {
        val targetId = mediaRef.externalIds[ProviderId("anilist")]?.toIntOrNull()
            ?: mediaRef.id.value.substringAfter(":").toIntOrNull()
            ?: return@of emptyList()

        val graphQuery = """
        query {
          Media(id: $targetId, type: ANIME) {
            relations {
              edges {
                relationType
                node {
                  id idMal title { romaji english native } coverImage { extraLarge large } averageScore episodes
                }
              }
            }
          }
        }
        """.trimIndent()

        val jsonResponse = executeGraphQL(graphQuery)
        val edges = jsonResponse["data"]
            ?.jsonObject
            ?.get("Media")
            ?.jsonObject
            ?.get("relations")
            ?.jsonObject
            ?.get("edges")
            ?.jsonArray

        edges?.mapNotNull { edge ->
            edge.jsonObject["node"]?.jsonObject?.let { parseMediaItem(it) }
        }.orEmpty()
    }

    override suspend fun onEpisodeWatched(mediaRef: MediaRef, episode: Int): ProviderResult<Unit> = ProviderResult.of {
        val token = accessToken() ?: return@of
        val targetId = mediaRef.externalIds[ProviderId("anilist")]?.toIntOrNull()
            ?: mediaRef.id.value.substringAfter(":").toIntOrNull() ?: return@of

        val mutation = """
        mutation {
          SaveMediaListEntry(mediaId: $targetId, progress: $episode) {
            id progress status
          }
        }
        """.trimIndent()

        executeGraphQL(mutation, token = token)
        Unit
    }

    override suspend fun syncStatus(mediaRef: MediaRef, status: UserMediaStatus): ProviderResult<Unit> = ProviderResult.of {
        val token = accessToken() ?: return@of
        val targetId = mediaRef.externalIds[ProviderId("anilist")]?.toIntOrNull()
            ?: mediaRef.id.value.substringAfter(":").toIntOrNull() ?: return@of

        val anilistStatus = when (status) {
            UserMediaStatus.WATCHING -> "CURRENT"
            UserMediaStatus.PLANNED -> "PLANNING"
            UserMediaStatus.COMPLETED -> "COMPLETED"
            UserMediaStatus.DROPPED -> "DROPPED"
            UserMediaStatus.ON_HOLD -> "PAUSED"
        }

        val mutation = """
        mutation {
          SaveMediaListEntry(mediaId: $targetId, status: $anilistStatus) {
            id status
          }
        }
        """.trimIndent()

        executeGraphQL(mutation, token = token)
        Unit
    }

    private suspend fun executeGraphQL(query: String, token: String? = accessToken()): JsonObject {
        val url = "https://graphql.anilist.co"
        val requestBody = buildJsonObject { put("query", query) }
        val responseText = client().post(url) {
            header("User-Agent", "Zenith-App/2.0")
            token?.let { header("Authorization", "Bearer $it") }
            contentType(ContentType.Application.Json)
            setBody(requestBody.toString())
        }.bodyAsText()

        val parsed = json.decodeFromString<JsonObject>(responseText)
        if ("errors" in parsed) {
            val errorMsg = parsed["errors"].toString()
            error("AniList GraphQL error: $errorMsg")
        }
        return parsed
    }

    private fun parseMediaItem(obj: JsonObject): CatalogMediaItem? {
        val id = obj["id"]?.jsonPrimitive?.intOrNull ?: return null
        val idMal = obj["idMal"]?.jsonPrimitive?.intOrNull
        val titleObj = obj["title"]?.jsonObject
        val romaji = titleObj?.get("romaji").asString()
        val english = titleObj?.get("english").asString()
        val native = titleObj?.get("native").asString()
        val displayTitle = english ?: romaji ?: native ?: "Anime #$id"

        val poster = obj["coverImage"]?.jsonObject?.let {
            it["extraLarge"].asString() ?: it["large"].asString()
        }
        val score = obj["averageScore"]?.jsonPrimitive?.doubleOrNull?.let { it / 10.0 }
        val year = obj["seasonYear"]?.jsonPrimitive?.intOrNull
            ?: obj["startDate"]?.jsonObject?.get("year")?.jsonPrimitive?.intOrNull
        val episodes = obj["episodes"]?.jsonPrimitive?.intOrNull

        return CatalogMediaItem(
            mediaRef = MediaRef(
                id = MediaId("anilist:$id"),
                externalIds = buildMap {
                    put(ProviderId("anilist"), id.toString())
                    if (idMal != null) put(ProviderId("mal"), idMal.toString())
                },
            ),
            title = displayTitle,
            originalTitle = romaji ?: native,
            posterUrl = poster,
            score = score,
            year = year,
            totalEpisodes = episodes,
        )
    }

    private fun parseMediaDetails(obj: JsonObject): CatalogMediaDetails {
        val id = obj["id"]?.jsonPrimitive?.intOrNull ?: 0
        val idMal = obj["idMal"]?.jsonPrimitive?.intOrNull
        val titleObj = obj["title"]?.jsonObject
        val romaji = titleObj?.get("romaji").asString()
        val english = titleObj?.get("english").asString()
        val native = titleObj?.get("native").asString()
        val displayTitle = english ?: romaji ?: native ?: "Anime #$id"

        val description = obj["description"].asString()
        val poster = obj["coverImage"]?.jsonObject?.let {
            it["extraLarge"].asString() ?: it["large"].asString()
        }
        val banner = obj["bannerImage"].asString()
        val score = obj["averageScore"]?.jsonPrimitive?.doubleOrNull?.let { it / 10.0 }
        val year = obj["seasonYear"]?.jsonPrimitive?.intOrNull
            ?: obj["startDate"]?.jsonObject?.get("year")?.jsonPrimitive?.intOrNull
        val episodes = obj["episodes"]?.jsonPrimitive?.intOrNull
        val status = obj["status"].asString()

        val genres = obj["genres"]?.jsonArray?.mapNotNull {
            it.asString()
        }.orEmpty()

        val studios = obj["studios"]?.jsonObject
            ?.get("nodes")?.jsonArray
            ?.mapNotNull { it.jsonObject["name"].asString() }
            .orEmpty()

        return CatalogMediaDetails(
            mediaRef = MediaRef(
                id = MediaId("anilist:$id"),
                externalIds = buildMap {
                    put(ProviderId("anilist"), id.toString())
                    if (idMal != null) put(ProviderId("mal"), idMal.toString())
                },
            ),
            title = displayTitle,
            originalTitle = romaji ?: native,
            description = description,
            posterUrl = poster,
            bannerUrl = banner,
            score = score,
            year = year,
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
