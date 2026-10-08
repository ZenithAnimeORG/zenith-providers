package com.pilldev.zenith.providers.builtin.provider

import com.pilldev.zenith.provider.BaseZenithProvider
import com.pilldev.zenith.provider.SubtitleSourceProvider
import com.pilldev.zenith.provider.context.ProviderContext
import com.pilldev.zenith.provider.model.BuiltInProviders
import com.pilldev.zenith.provider.model.ProviderCapability
import com.pilldev.zenith.provider.model.ProviderMetadata
import com.pilldev.zenith.provider.model.ProviderResult
import com.pilldev.zenith.provider.model.ProviderSettingSpec
import com.pilldev.zenith.provider.model.ProviderSubtitle
import com.pilldev.zenith.provider.model.ProviderTestResult
import com.pilldev.zenith.provider.model.measureTest
import com.pilldev.zenith.providers.builtin.mapper.toProviderSubtitle
import com.pilldev.zenith.providers.builtin.subtitles.OpenSubtitlesManager

public open class OpenSubtitlesProvider(
    private val openSubtitlesManager: OpenSubtitlesManager,
) : BaseZenithProvider(),
    SubtitleSourceProvider {
    override val metadata: ProviderMetadata =
        ProviderMetadata(
            id = BuiltInProviders.OPENSUBTITLES,
            name = "OpenSubtitles",
            version = "1.0.0",
            capabilities = setOf(ProviderCapability.SUBTITLES),
            description = "Глобальная база субтитров OpenSubtitles.com",
            pros = listOf("Огромная коллекция языков и переводов", "Субтитры для редких тайтлов"),
            cons = listOf("Лимиты API и возможная рассинхронизация с видео"),
            settingsSchema =
                listOf(
                    ProviderSettingSpec(
                        key = "api_key",
                        label = "OpenSubtitles API Key",
                        description = "Персональный API-ключ для загрузки субтитров",
                    ),
                ),
        )

    override suspend fun test(context: ProviderContext): ProviderTestResult =
        measureTest {
            val result = openSubtitlesManager.searchSubtitles("Horimiya", "rus", 1, 1)
            "OpenSubtitles API активен. Найдено субтитров: ${result.size}"
        }

    override suspend fun searchSubtitles(
        query: String,
        languages: String,
        episodeNumber: Int?,
        seasonNumber: Int?,
    ): ProviderResult<List<ProviderSubtitle>> =
        ProviderResult.of {
            val subs = openSubtitlesManager.searchSubtitles(query, languages, episodeNumber, seasonNumber)
            subs.map { it.toProviderSubtitle() }
        }

    override suspend fun getDownloadLink(fileId: Long): ProviderResult<String?> =
        ProviderResult.of {
            openSubtitlesManager.getDownloadLink(fileId)
        }
}
