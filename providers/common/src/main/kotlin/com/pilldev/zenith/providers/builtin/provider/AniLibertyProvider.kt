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
import com.pilldev.zenith.providers.builtin.mapper.toProviderVideoSource
import com.pilldev.zenith.providers.builtin.parser.AniLibriaParser

public open class AniLibertyProvider(
    private val aniLibriaParser: AniLibriaParser,
) : BaseZenithProvider(),
    MediaSourceProvider,
    PosterSourceProvider {
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
            val result = aniLibriaParser.getSources(47917, "Bocchi the Rock!", "Одинокий рокер!")
            "AniLiberty API активен. Найдено озвучек: ${result.sources.size}"
        }

    override suspend fun getSources(
        animeId: Int,
        animeName: String,
        russianName: String?,
    ): ProviderResult<List<ProviderVideoSource>> =
        ProviderResult.of {
            val result = aniLibriaParser.getSources(animeId, animeName, russianName)
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
                aniLibriaParser.parseResults(
                    runCatching { aniLibriaParser.aniLibertyApi.appSearch(cleanName) }.getOrNull()
                        ?: aniLibriaParser.aniLibertyApi.searchReleases(cleanName),
                )
            val poster = releases.firstOrNull()?.poster
            val rawUrl = poster?.optimized?.preview ?: poster?.preview
            rawUrl?.let { UrlNormalizer.resolve(it, baseUrl = resolveBaseDomain()) }
        }
}
