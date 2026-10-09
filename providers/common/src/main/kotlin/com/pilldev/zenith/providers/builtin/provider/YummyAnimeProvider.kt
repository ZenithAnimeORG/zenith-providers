package com.pilldev.zenith.providers.builtin.provider

import com.pilldev.zenith.domain.model.toProviderMirrorSpec
import com.pilldev.zenith.domain.repository.AppDispatchers
import com.pilldev.zenith.provider.BaseZenithProvider
import com.pilldev.zenith.provider.MediaSourceProvider
import com.pilldev.zenith.provider.PosterSourceProvider
import com.pilldev.zenith.provider.context.ProviderContext
import com.pilldev.zenith.provider.matcher.AnimeTitleMatcher
import com.pilldev.zenith.provider.model.BuiltInProviders
import com.pilldev.zenith.provider.model.ProviderCapability
import com.pilldev.zenith.provider.model.ProviderMetadata
import com.pilldev.zenith.provider.model.ProviderResult
import com.pilldev.zenith.provider.model.ProviderSettingSpec
import com.pilldev.zenith.provider.model.ProviderTestResult
import com.pilldev.zenith.provider.model.ProviderVideoSource
import com.pilldev.zenith.provider.model.measureTest
import com.pilldev.zenith.provider.net.UrlNormalizer
import com.pilldev.zenith.providers.builtin.api.YummyAnimeApi
import com.pilldev.zenith.providers.builtin.api.YummyAnimeResult
import com.pilldev.zenith.providers.builtin.mapper.toProviderVideoSource
import com.pilldev.zenith.providers.builtin.parser.YummyAnimeParser
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json

public open class YummyAnimeProvider(
    private var yummyAnimeParser: YummyAnimeParser? = null,
) : BaseZenithProvider(),
    MediaSourceProvider,
    PosterSourceProvider {

    constructor() : this(null)

    override val metadata: ProviderMetadata =
        ProviderMetadata(
            id = BuiltInProviders.YUMMYANIME,
            name = "YummyAnime",
            version = "1.0.0",
            capabilities = setOf(ProviderCapability.MEDIA_SOURCE, ProviderCapability.POSTER_SOURCE),
            description = "Надёжный альтернативный источник",
            pros = listOf("Стабильный доступ и большое количество озвученных тайтлов"),
            cons = listOf("Зеркала иногда требуют обхода"),
            settingsSchema =
                listOf(
                    ProviderSettingSpec(
                        key = "domain",
                        label = "Домен YummyAnime",
                        defaultValue = "api.yani.tv",
                        description = "API endpoint или зеркало",
                    ),
                    ProviderSettingSpec(
                        key = "public_token",
                        label = "Public Token",
                        type = com.pilldev.zenith.provider.model.SettingType.PASSWORD,
                        description = "Публичный токен YummyAnime API (оставьте пустым для встроенного)",
                    ),
                    ProviderSettingSpec(
                        key = "private_token",
                        label = "Private Token",
                        type = com.pilldev.zenith.provider.model.SettingType.PASSWORD,
                        description = "Приватный токен авторизации YummyAnime API (оставьте пустым для встроенного)",
                    ),
                ),
            mirrors = com.pilldev.zenith.domain.model.StandardMirrors.YUMMY_ANIME
                .map { it.toProviderMirrorSpec() },
        )

    private fun ensureParser(): YummyAnimeParser {
        val existing = yummyAnimeParser
        if (existing != null) return existing

        val ctx = context
        val client = ctx?.httpClient ?: HttpClient()
        val api = YummyAnimeApi(
            client = client,
            playerSettingsManager = null,
            getPublicToken = { ctx?.getSetting("public_token")?.trim().orEmpty() },
            getPrivateToken = { ctx?.getSetting("private_token")?.trim().orEmpty() },
        )
        val parser = YummyAnimeParser(
            yummyAnimeApi = api,
            json = Json {
                ignoreUnknownKeys = true
                isLenient = true
                coerceInputValues = true
            },
            playerSettingsManager = null,
            appDispatchers = object : AppDispatchers {
                override val main = Dispatchers.Main
                override val io = Dispatchers.IO
                override val default = Dispatchers.Default
                override val unconfined = Dispatchers.Unconfined
            },
        )
        yummyAnimeParser = parser
        return parser
    }

    override suspend fun test(context: ProviderContext): ProviderTestResult =
        measureTest {
            val parser = ensureParser()
            val result = parser.getSources(54856, "Horimiya: Piece", "Хоримия: Кусочек")
            "YummyAnime API активен. Найдено озвучек: ${result.sources.size}"
        }

    override suspend fun getSources(
        animeId: Int,
        animeName: String,
        russianName: String?,
    ): ProviderResult<List<ProviderVideoSource>> =
        ProviderResult.of {
            val parser = ensureParser()
            val result = parser.getSources(animeId, animeName, russianName)
            result.sources.map { it.toProviderVideoSource() }
        }

    override suspend fun getPoster(
        animeId: Int,
        animeName: String,
    ): ProviderResult<String?> =
        ProviderResult.of {
            val parser = ensureParser()
            val cleanName = AnimeTitleMatcher.cleanForSearch(animeName)
            val yummySearchElement = parser.yummyAnimeApi.search(cleanName)
            val results: List<YummyAnimeResult> = parser.parseSearchResults(yummySearchElement)
            val match = results.find { it.remote_ids?.shikimori_id == animeId } ?: results.firstOrNull()
            val poster = match?.poster
            val rawUrl = poster?.big ?: poster?.fullsize ?: poster?.medium
            rawUrl?.let { UrlNormalizer.resolve(it) }
        }
}

public class YummyAnimeProviderFactory : com.pilldev.zenith.provider.ZenithProviderFactory {
    override fun create(manifest: com.pilldev.zenith.provider.model.PluginManifest): com.pilldev.zenith.provider.ZenithProvider =
        YummyAnimeProvider()
}
