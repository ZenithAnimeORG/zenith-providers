package com.pilldev.zenith.providers.builtin.provider

import com.pilldev.zenith.domain.model.toProviderMirrorSpec
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
import com.pilldev.zenith.providers.builtin.api.YummyAnimeResult
import com.pilldev.zenith.providers.builtin.mapper.toProviderVideoSource
import com.pilldev.zenith.providers.builtin.parser.YummyAnimeParser

public open class YummyAnimeProvider(
    private val yummyAnimeParser: YummyAnimeParser,
) : BaseZenithProvider(),
    MediaSourceProvider,
    PosterSourceProvider {
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
                ),
            mirrors = com.pilldev.zenith.domain.model.StandardMirrors.YUMMY_ANIME
                .map { it.toProviderMirrorSpec() },
        )

    override suspend fun test(context: ProviderContext): ProviderTestResult =
        measureTest {
            val result = yummyAnimeParser.getSources(54856, "Horimiya: Piece", "Хоримия: Кусочек")
            "YummyAnime API активен. Найдено озвучек: ${result.sources.size}"
        }

    override suspend fun getSources(
        shikimoriId: Int,
        animeName: String,
        russianName: String?,
    ): ProviderResult<List<ProviderVideoSource>> =
        ProviderResult.of {
            val result = yummyAnimeParser.getSources(shikimoriId, animeName, russianName)
            result.sources.map { it.toProviderVideoSource() }
        }

    override suspend fun getPoster(
        animeId: Int,
        animeName: String,
    ): ProviderResult<String?> =
        ProviderResult.of {
            val cleanName = AnimeTitleMatcher.cleanForSearch(animeName)
            val yummySearchElement = yummyAnimeParser.yummyAnimeApi.search(cleanName)
            val results: List<YummyAnimeResult> = yummyAnimeParser.parseSearchResults(yummySearchElement)
            val match = results.find { it.remote_ids?.shikimori_id == animeId } ?: results.firstOrNull()
            val poster = match?.poster
            val rawUrl = poster?.big ?: poster?.fullsize ?: poster?.medium
            rawUrl?.let { UrlNormalizer.resolve(it) }
        }
}
