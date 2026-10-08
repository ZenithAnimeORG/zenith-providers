package com.pilldev.zenith.providers.builtin.api

import com.pilldev.zenith.providers.builtin.api.ktorfit.AniLibertyCatalogResponseDto
import com.pilldev.zenith.providers.builtin.api.ktorfit.AniLibertyKtorfitApi
import com.pilldev.zenith.providers.builtin.api.ktorfit.AniLibertyLoginRequestDto
import com.pilldev.zenith.providers.builtin.api.ktorfit.AniLibertyLoginResponseDto
import com.pilldev.zenith.providers.builtin.api.ktorfit.createAniLibertyKtorfitApi
import com.pilldev.zenith.providers.builtin.net.BuiltInEndpoints
import de.jensklingenberg.ktorfit.Ktorfit
import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

open class AniLibertyApi(
    open val ktorfitApi: AniLibertyKtorfitApi,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    },
) {
    constructor(
        client: HttpClient,
        json: Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        },
    ) : this(
        Ktorfit
            .Builder()
            .httpClient(client)
            .baseUrl(BuiltInEndpoints.ANI_LIBERTY)
            .build()
            .createAniLibertyKtorfitApi(),
        json,
    )

    constructor() : this(HttpClient())

    open suspend fun searchReleases(
        search: String,
        limit: Int = 20,
    ): JsonElement {
        val dto = ktorfitApi.getCatalogReleases(search = search, limit = limit)
        return json.encodeToJsonElement(AniLibertyCatalogResponseDto.serializer(), dto)
    }

    open suspend fun appSearch(
        query: String,
        limit: Int = 20,
    ): JsonElement {
        val dto = ktorfitApi.appSearch(query = query, limit = limit)
        return json.encodeToJsonElement(
            kotlinx.serialization.builtins.ListSerializer(
                com.pilldev.zenith.providers.builtin.api.ktorfit.AniLibertyReleaseDto
                    .serializer()
            ),
            dto,
        )
    }

    open suspend fun getLatestReleases(
        limit: Int = 20,
        include: String = "episodes",
    ): JsonElement {
        val dto = ktorfitApi.getLatestReleases(limit = limit, include = include)
        return json.encodeToJsonElement(AniLibertyCatalogResponseDto.serializer(), dto)
    }

    open suspend fun getRelease(
        idOrAlias: String,
        include: String = "episodes",
    ): JsonElement {
        val dto = ktorfitApi.getRelease(idOrAlias = idOrAlias, include = include)
        return json.encodeToJsonElement(
            com.pilldev.zenith.providers.builtin.api.ktorfit.AniLibertyReleaseDto
                .serializer(),
            dto,
        )
    }

    open suspend fun login(
        login: String,
        pass: String
    ): AniLibertyLoginResponseDto = ktorfitApi.login(AniLibertyLoginRequestDto(login, pass))

    open suspend fun login(request: AniLibertyLoginRequestDto): AniLibertyLoginResponseDto = ktorfitApi.login(request)

    open suspend fun getProfile() = ktorfitApi.getProfile()
}
