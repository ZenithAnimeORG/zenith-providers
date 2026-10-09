package com.pilldev.zenith.providers.builtin.provider

import com.pilldev.zenith.domain.model.toProviderMirrorSpec
import com.pilldev.zenith.provider.BaseZenithProvider
import com.pilldev.zenith.provider.MediaSourceProvider
import com.pilldev.zenith.provider.PosterSourceProvider
import com.pilldev.zenith.provider.context.ProviderContext
import com.pilldev.zenith.provider.matcher.AnimeTitleMatcher
import com.pilldev.zenith.provider.model.BuiltInProviders
import com.pilldev.zenith.provider.model.ProviderCapability
import com.pilldev.zenith.provider.model.ProviderEpisode
import com.pilldev.zenith.provider.model.ProviderMediaStream
import com.pilldev.zenith.provider.model.ProviderMetadata
import com.pilldev.zenith.provider.model.ProviderResult
import com.pilldev.zenith.provider.model.ProviderSettingSpec
import com.pilldev.zenith.provider.model.ProviderTestResult
import com.pilldev.zenith.provider.model.ProviderVideoSource
import com.pilldev.zenith.provider.model.measureTest
import com.pilldev.zenith.provider.net.BrowserHeaders
import com.pilldev.zenith.provider.net.UrlNormalizer
import com.pilldev.zenith.providers.builtin.api.AniLibertyApi
import com.pilldev.zenith.providers.builtin.mapper.toProviderVideoSource
import com.pilldev.zenith.providers.builtin.parser.AniLibriaParser

public open class AniLibertyProvider(
    private var aniLibriaParser: AniLibriaParser? = null,
) : BaseZenithProvider(),
    MediaSourceProvider,
    PosterSourceProvider {

    constructor() : this(null)

    private fun ensureParser(): AniLibriaParser {
        val existing = aniLibriaParser
        if (existing != null) return existing
        val ctx = context
        val client = ctx?.httpClient ?: io.ktor.client.HttpClient()
        val json = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }
        val api = AniLibertyApi(client, json)
        val parser = AniLibriaParser(
            aniLibertyApi = api,
            json = json,
            appDispatchers = object : com.pilldev.zenith.domain.repository.AppDispatchers {
                override val main = kotlinx.coroutines.Dispatchers.Main
                override val io = kotlinx.coroutines.Dispatchers.IO
                override val default = kotlinx.coroutines.Dispatchers.Default
                override val unconfined = kotlinx.coroutines.Dispatchers.Unconfined
            },
        )
        aniLibriaParser = parser
        return parser
    }

    override val metadata: ProviderMetadata =
        ProviderMetadata(
            id = BuiltInProviders.ANILIBRIA,
            name = "AniLiberty",
            version = "1.0.0",
            capabilities = setOf(ProviderCapability.MEDIA_SOURCE, ProviderCapability.POSTER_SOURCE),
            description = "Прямые релизы проекта AniLibria",
            pros = listOf("Стабильный прямой доступ без блокировок", "Большой выбор озвученных тайтлов"),
            cons = listOf("Ограничен только релизами AniLibria"),
            settingsSchema =
                listOf(
                    ProviderSettingSpec(
                        key = "base_url",
                        label = "Базовый адрес AniLiberty",
                        defaultValue = "https://anilibria.top/api/v1/",
                        description = "API endpoint для получения данных",
                    ),
                ),
            mirrors = com.pilldev.zenith.domain.model.StandardMirrors.ANI_LIBRIA
                .map { it.toProviderMirrorSpec() },
        )

    private suspend fun resolveBaseDomain(): String = effectiveMirror().ifBlank { getSetting("base_url")?.substringBefore("/api") ?: "https://anilibria.top" }.removeSuffix("/")

    override suspend fun test(context: ProviderContext): ProviderTestResult =
        measureTest {
            val result = ensureParser().getSources(47917, "Bocchi the Rock!", "Одинокий рокер!")
            "AniLiberty API активен. Найдено озвучек: ${result.sources.size}"
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

    override suspend fun resolveStream(
        episode: ProviderEpisode,
    ): ProviderResult<List<ProviderMediaStream>> =
        ProviderResult.Success(
            listOf(
                ProviderMediaStream(
                    url = episode.url,
                    quality = "Auto",
                    headers = BrowserHeaders.standardHeaders(referer = "${resolveBaseDomain()}/"),
                    isHls = episode.url.contains(".m3u8"),
                ),
            ),
        )

    override suspend fun getPoster(
        animeId: Int,
        animeName: String,
    ): ProviderResult<String?> =
        ProviderResult.of {
            val cleanName = AnimeTitleMatcher.cleanForSearch(animeName)
            val releases =
                ensureParser().parseResults(
                    runCatching { ensureParser().aniLibertyApi.appSearch(cleanName) }.getOrNull()
                        ?: ensureParser().aniLibertyApi.searchReleases(cleanName),
                )
            val poster = releases.firstOrNull()?.poster
            val rawUrl = poster?.optimized?.preview ?: poster?.preview
            rawUrl?.let { UrlNormalizer.resolve(it, baseUrl = resolveBaseDomain()) }
        }
}

public class AniLibertyProviderFactory : com.pilldev.zenith.provider.ZenithProviderFactory {
    override fun create(manifest: com.pilldev.zenith.provider.model.PluginManifest): com.pilldev.zenith.provider.ZenithProvider =
        AniLibertyProvider()
}
