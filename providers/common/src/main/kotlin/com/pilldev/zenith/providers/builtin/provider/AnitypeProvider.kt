package com.pilldev.zenith.providers.builtin.provider

import com.pilldev.zenith.provider.BaseZenithProvider
import com.pilldev.zenith.provider.MediaSourceProvider
import com.pilldev.zenith.provider.SkipTimingsProvider
import com.pilldev.zenith.provider.context.ProviderContext
import com.pilldev.zenith.provider.model.BuiltInProviders
import com.pilldev.zenith.provider.model.ProviderCapability
import com.pilldev.zenith.provider.model.ProviderMetadata
import com.pilldev.zenith.provider.model.ProviderResult
import com.pilldev.zenith.provider.model.ProviderSettingSpec
import com.pilldev.zenith.provider.model.ProviderTestResult
import com.pilldev.zenith.provider.model.ProviderVideoSource
import com.pilldev.zenith.provider.model.SkipInterval
import com.pilldev.zenith.provider.model.measureTest
import com.pilldev.zenith.providers.builtin.mapper.toProviderVideoSource
import com.pilldev.zenith.providers.builtin.parser.AnitypeParser

public open class AnitypeProvider(
    private var anitypeParser: AnitypeParser? = null,
) : BaseZenithProvider(),
    MediaSourceProvider,
    SkipTimingsProvider {

    constructor() : this(null)

    private fun ensureParser(): AnitypeParser {
        val existing = anitypeParser
        if (existing != null) return existing
        val ctx = context
        val client = ctx?.httpClient ?: io.ktor.client.HttpClient()
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }
        val parser = AnitypeParser(
            client = client,
            json = json,
            playerSettingsManager = null,
            appDispatchers = object : com.pilldev.zenith.domain.repository.AppDispatchers {
                override val main = kotlinx.coroutines.Dispatchers.Main
                override val io = kotlinx.coroutines.Dispatchers.IO
                override val default = kotlinx.coroutines.Dispatchers.Default
                override val unconfined = kotlinx.coroutines.Dispatchers.Unconfined
            },
        )
        anitypeParser = parser
        return parser
    }

    override val metadata: ProviderMetadata =
        ProviderMetadata(
            id = BuiltInProviders.ANITYPE,
            name = "AniType",
            version = "1.0.0",
            capabilities = setOf(ProviderCapability.MEDIA_SOURCE, ProviderCapability.SKIP_TIMINGS),
            description = "Эксклюзивный каталог с высокой чёткостью",
            pros = listOf("4K-апскейл тайтлы", "Точные таймкоды для релизов AniType"),
            cons = listOf("Для доступа требуется авторизация и подписка в AniType"),
            settingsSchema =
                listOf(
                    ProviderSettingSpec(
                        key = "domain",
                        label = "Домен AniType",
                        defaultValue = "https://wwwanitype.fun/",
                        description = "API endpoint или зеркало",
                    ),
                ),
        )

    override suspend fun test(context: ProviderContext): ProviderTestResult =
        measureTest {
            val result = ensureParser().getSources(54856, "Horimiya: Piece", "Хоримия: Кусочек")
            "AniType API активен. Найдено озвучек: ${result.sources.size}"
        }

    override suspend fun getSources(
        animeId: Int,
        animeName: String,
        russianName: String?,
    ): ProviderResult<List<ProviderVideoSource>> =
        ProviderResult.of {
            val result = ensureParser().getSources(animeId, animeName, russianName)
            result.sources.map { it.toProviderVideoSource() }
        }

    override suspend fun getSkipTimes(
        malId: Int,
        episodeNumber: Int,
        episodeLength: Double?,
        animeId: Int,
        translationName: String?,
    ): ProviderResult<List<SkipInterval>> =
        ProviderResult.of {
            val idToUse = if (animeId > 0) animeId else malId
            ensureParser().getSkips(idToUse, episodeNumber, translationName)
        }
}

public class AnitypeProviderFactory : com.pilldev.zenith.provider.ZenithProviderFactory {
    override fun create(manifest: com.pilldev.zenith.provider.model.PluginManifest): com.pilldev.zenith.provider.ZenithProvider =
        AnitypeProvider()
}
