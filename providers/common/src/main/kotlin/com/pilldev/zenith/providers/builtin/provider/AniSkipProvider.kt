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
import com.pilldev.zenith.providers.builtin.api.AniSkipApi

public open class AniSkipProvider(
    private val aniSkipApi: AniSkipApi,
) : BaseZenithProvider(),
    SkipTimingsProvider {
    override val metadata: ProviderMetadata =
        ProviderMetadata(
            id = BuiltInProviders.ANISKIP,
            name = "AniSkip",
            version = "1.0.0",
            capabilities = setOf(ProviderCapability.SKIP_TIMINGS),
            description = "База таймингов опенингов и эндингов",
            pros = listOf("Точные тайминги для большинства аниме"),
            cons = listOf("Иногда отсутствуют тайминги для новых онгоингов"),
            settingsSchema =
                listOf(
                    ProviderSettingSpec(
                        key = "base_url",
                        label = "API AniSkip",
                        defaultValue = "https://api.aniskip.com/v2/",
                        description = "Сервер таймингов пропуска",
                    ),
                ),
        )

    override suspend fun test(context: ProviderContext): ProviderTestResult =
        measureTest {
            val response = aniSkipApi.getSkipTimes(54856, 1, 1420.0)
            "AniSkip API активен (найдено интервалов: ${response.results.size})"
        }

    override suspend fun getSkipTimes(
        malId: Int,
        episodeNumber: Int,
        episodeLength: Double?,
        animeId: Int,
        translationName: String?,
    ): ProviderResult<List<SkipInterval>> =
        ProviderResult.of {
            val response = aniSkipApi.getSkipTimes(malId, episodeNumber, episodeLength)
            response.results.mapNotNull { result ->
                val interval = result.interval
                val start = result.start
                val end = result.end
                if (interval != null) {
                    SkipInterval(
                        startTime = interval.startTime,
                        endTime = interval.endTime,
                        skipType = result.skipType,
                        episodeLength = result.episodeLength,
                    )
                } else if (start != null && end != null) {
                    SkipInterval(
                        startTime = start,
                        endTime = end,
                        skipType = result.skipType,
                        episodeLength = result.episodeLength,
                    )
                } else {
                    null
                }
            }
        }
}
