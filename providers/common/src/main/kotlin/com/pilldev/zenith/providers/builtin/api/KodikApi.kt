package com.pilldev.zenith.providers.builtin.api

import com.pilldev.zenith.providers.builtin.api.ktorfit.KodikKtorfitApi
import com.pilldev.zenith.providers.builtin.api.ktorfit.createKodikKtorfitApi
import de.jensklingenberg.ktorfit.Ktorfit
import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement

open class KodikApi(
    open val ktorfitApi: KodikKtorfitApi,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    constructor(
        client: HttpClient,
        json: Json = Json { ignoreUnknownKeys = true },
    ) : this(
        Ktorfit
            .Builder()
            .httpClient(client)
            .baseUrl(com.pilldev.zenith.providers.builtin.net.BuiltInEndpoints.KODIK)
            .build()
            .createKodikKtorfitApi(),
        json,
    )

    constructor() : this(HttpClient())

    open suspend fun searchByShikimori(
        token: String?,
        shikimoriId: Int,
        withEpisodes: Boolean = true,
        withMaterial: Boolean = true,
    ): JsonElement {
        val dto = ktorfitApi.search(
            token = token ?: "",
            shikimoriId = shikimoriId,
            withEpisodes = withEpisodes,
            withMaterialData = withMaterial,
        )
        return json.encodeToJsonElement(dto)
    }

    open suspend fun searchByTitle(
        token: String?,
        title: String,
        withEpisodes: Boolean = true,
        withMaterial: Boolean = true,
    ): JsonElement {
        val dto = ktorfitApi.search(
            token = token ?: "",
            title = title,
            withEpisodes = withEpisodes,
            withMaterialData = withMaterial,
        )
        return json.encodeToJsonElement(dto)
    }
}
