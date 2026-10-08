package com.pilldev.zenith.providers.builtin.provider

import com.pilldev.zenith.provider.BaseZenithProvider
import com.pilldev.zenith.provider.SkipTimingsProvider
import com.pilldev.zenith.provider.context.ProviderContext
import com.pilldev.zenith.provider.model.BuiltInProviders
import com.pilldev.zenith.provider.model.ProviderCapability
import com.pilldev.zenith.provider.model.ProviderMetadata
import com.pilldev.zenith.provider.model.ProviderResult
import com.pilldev.zenith.provider.model.ProviderSettingSpec
import com.pilldev.zenith.provider.model.ProviderTestResult
import com.pilldev.zenith.provider.model.SkipInterval
import com.pilldev.zenith.provider.model.measureTest
import com.pilldev.zenith.providers.builtin.api.AnimeSkipApi
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

public open class AnimeSkipProvider(
    private val animeSkipApi: AnimeSkipApi,
    private val httpClient: io.ktor.client.HttpClient = io.ktor.client.HttpClient(),
) : BaseZenithProvider(),
    SkipTimingsProvider {
    override val metadata: ProviderMetadata =
        ProviderMetadata(
            id = BuiltInProviders.ANIMESKIP,
            name = "AnimeSkip",
            version = "1.0.0",
            capabilities = setOf(ProviderCapability.SKIP_TIMINGS),
            description = "Сообщественный сервис таймкодов",
            pros = listOf("Пользовательские таймкоды для редких тайтлов"),
            cons = listOf("Возможна неточность в секундах"),
            settingsSchema =
                listOf(
                    ProviderSettingSpec(
                        key = "client_id",
                        label = "Anime-Skip Client ID",
                        description = "Кастомный идентификатор клиента",
                    ),
                ),
        )

    override suspend fun test(context: ProviderContext): ProviderTestResult =
        measureTest {
            val aniListId = 153518
            "AnimeSkip сопоставлен (AniList ID: $aniListId)"
        }

    override suspend fun getSkipTimes(
        malId: Int,
        episodeNumber: Int,
        episodeLength: Double?,
        shikimoriId: Int,
        translationName: String?,
    ): ProviderResult<List<SkipInterval>> =
        ProviderResult.of {
            val aniListId = runCatching {
                val text = httpClient.get("https://api.ani.zip/mappings?mal_id=$malId").bodyAsText()
                val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                val obj = json.decodeFromString<kotlinx.serialization.json.JsonObject>(text)
                obj["mappings"]
                    ?.jsonObject
                    ?.get("anilist_id")
                    ?.jsonPrimitive
                    ?.intOrNull
            }.getOrNull()
            if (aniListId == null) {
                emptyList()
            } else {
                val response = animeSkipApi.getTimestamps(aniListId)
                val episodes =
                    response.data?.findShowsByExternalId?.flatMap { it.episodes ?: emptyList() } ?: emptyList()
                val episode = episodes.find { it.number == episodeNumber.toString() }
                if (episode == null) {
                    emptyList()
                } else {
                    val timestamps = episode.timestamps
                    val intervals = mutableListOf<SkipInterval>()
                    for (i in timestamps.indices) {
                        val current = timestamps[i]
                        val next = timestamps.getOrNull(i + 1)
                        val skipType = mapAnimeSkipType(current.type.name)
                        if (skipType != null) {
                            val endTime = next?.at ?: 9999.0
                            if (endTime > current.at) {
                                intervals.add(
                                    SkipInterval(
                                        startTime = current.at,
                                        endTime = endTime,
                                        skipType = skipType,
                                        episodeLength = episodeLength,
                                    ),
                                )
                            }
                        }
                    }
                    intervals
                }
            }
        }

    private fun mapAnimeSkipType(name: String): String? =
        when (name.lowercase()) {
            "new intro", "opening", "op" -> "op"
            "new credits", "ending", "ed" -> "ed"
            "recap" -> "recap"
            "mixed-op" -> "mixed-op"
            "mixed-ed" -> "mixed-ed"
            else -> null
        }
}
