package com.pilldev.zenith.providers.builtin.api

import com.pilldev.zenith.domain.repository.PlayerSettingsRepository
import com.pilldev.zenith.providers.builtin.api.ktorfit.YummyKtorfitApi
import com.pilldev.zenith.providers.builtin.api.ktorfit.createYummyKtorfitApi
import com.pilldev.zenith.providers.builtin.net.BuiltInEndpoints
import com.pilldev.zenith.providers.builtin.net.BuiltInSecrets
import de.jensklingenberg.ktorfit.Ktorfit
import io.ktor.client.HttpClient
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * API client wrapper for YummyAnime service delegating to Ktorfit.
 */
open class YummyAnimeApi(
    open val ktorfitApi: YummyKtorfitApi,
    private val playerSettingsManager: PlayerSettingsRepository? = null,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    },
) {
    constructor(
        client: HttpClient,
        playerSettingsManager: PlayerSettingsRepository? = null,
        getPublicToken: () -> String = { "" },
        getPrivateToken: () -> String = { "" },
    ) : this(
        Ktorfit
            .Builder()
            .httpClient(
                client.config {
                    defaultRequest {
                        val pub = getPublicToken().ifBlank { BuiltInSecrets.YUMMY_PUBLIC_TOKEN }
                        val priv = getPrivateToken().ifBlank { BuiltInSecrets.YUMMY_PRIVATE_TOKEN }
                        if (pub.isNotBlank()) {
                            header("X-Application-Token", pub)
                        }
                        if (priv.isNotBlank()) {
                            header("Authorization", "Bearer $priv")
                        }
                    }
                }
            )
            .baseUrl(BuiltInEndpoints.YUMMY_ANIME)
            .build()
            .createYummyKtorfitApi(),
        playerSettingsManager,
    )

    constructor() : this(HttpClient(), null)

    /**
     * Searches for anime by a text query.
     */
    open suspend fun search(query: String): JsonElement {
        val dto = ktorfitApi.search(query)
        return json.encodeToJsonElement(
            YummySearchResponse.serializer(),
            dto,
        )
    }

    /**
     * Fetches video sources/episodes list for a specific yummy anime ID.
     */
    open suspend fun getVideos(animeId: Int): JsonElement {
        val dto = ktorfitApi.getVideos(animeId)
        return json.encodeToJsonElement(
            com.pilldev.zenith.providers.builtin.api.ktorfit.YummyVideosResponseDto
                .serializer(),
            dto,
        )
    }

    /**
     * Fetches full metadata details for a specific yummy anime ID.
     */
    open suspend fun getAnimeDetails(animeId: Int): JsonElement {
        val dto = ktorfitApi.getAnime(animeId)
        return json.encodeToJsonElement(
            YummyDetailsResponse.serializer(),
            dto,
        )
    }
}
