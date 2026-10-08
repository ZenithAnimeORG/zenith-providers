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
import io.ktor.client.HttpClient

public open class TheIntroDbProvider(
    private val client: HttpClient? = null,
) : BaseZenithProvider(),
    SkipTimingsProvider {
    override val metadata: ProviderMetadata =
        ProviderMetadata(
            id = BuiltInProviders.THEINTRODB,
            name = "TheIntroDB",
            version = "1.0.0",
            capabilities = setOf(ProviderCapability.SKIP_TIMINGS),
            description = "Глобальная база вступлений",
            pros = listOf("Универсальная база файлов"),
            cons = listOf("Сервис изначально создан для кино, аниме-тайминги практически отсутствуют"),
            settingsSchema =
                listOf(
                    ProviderSettingSpec(
                        key = "api_key",
                        label = "TheIntroDB API Key",
                        description = "Персональный API-ключ для TheIntroDB",
                    ),
                ),
        )

    override suspend fun test(context: ProviderContext): ProviderTestResult =
        measureTest {
            "TheIntroDB сервис готов к работе"
        }

    override suspend fun getSkipTimes(
        malId: Int,
        episodeNumber: Int,
        episodeLength: Double?,
        shikimoriId: Int,
        translationName: String?,
    ): ProviderResult<List<SkipInterval>> =
        ProviderResult.of {
            emptyList()
        }
}
