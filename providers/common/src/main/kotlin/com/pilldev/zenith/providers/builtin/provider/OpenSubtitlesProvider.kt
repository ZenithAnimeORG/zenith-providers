package com.pilldev.zenith.providers.builtin.provider

import com.pilldev.zenith.provider.BaseZenithProvider
import com.pilldev.zenith.provider.SubtitleSourceProvider
import com.pilldev.zenith.provider.ZenithProvider
import com.pilldev.zenith.provider.ZenithProviderFactory
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
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

public open class OpenSubtitlesProvider(
    private var openSubtitlesManager: OpenSubtitlesManager? = null,
) : BaseZenithProvider(),
    SubtitleSourceProvider {
    public constructor() : this(null)

    private fun ensureManager() {
        if (openSubtitlesManager == null) {
            openSubtitlesManager = OpenSubtitlesManager(
                HttpClient(),
                object : com.pilldev.zenith.domain.repository.AppDispatchers {
                    override val main: CoroutineDispatcher = Dispatchers.Main
                    override val io: CoroutineDispatcher = Dispatchers.IO
                    override val default: CoroutineDispatcher = Dispatchers.Default
                    override val unconfined: CoroutineDispatcher = Dispatchers.Unconfined
                },
            )
        }
    }

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
            ensureManager()
            val result = (openSubtitlesManager ?: error("OpenSubtitlesManager not initialized")).searchSubtitles("Horimiya", "rus", 1, 1)
            "OpenSubtitles API активен. Найдено субтитров: ${result.size}"
        }

    override suspend fun searchSubtitles(
        query: String,
        languages: String,
        episodeNumber: Int?,
        seasonNumber: Int?,
    ): ProviderResult<List<ProviderSubtitle>> =
        ProviderResult.of {
            ensureManager()
            val subs = (openSubtitlesManager ?: error("OpenSubtitlesManager not initialized")).searchSubtitles(query, languages, episodeNumber, seasonNumber)
            subs.map { it.toProviderSubtitle() }
        }

    override suspend fun getDownloadLink(fileId: Long): ProviderResult<String?> =
        ProviderResult.of {
            ensureManager()
            (openSubtitlesManager ?: error("OpenSubtitlesManager not initialized")).getDownloadLink(fileId)
        }
}

public class OpenSubtitlesProviderFactory : ZenithProviderFactory {
    override fun create(manifest: com.pilldev.zenith.provider.model.PluginManifest): ZenithProvider = OpenSubtitlesProvider()
}
