package com.pilldev.zenith.providers.builtin.provider

import com.fleeksoft.ksoup.Ksoup
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
import com.pilldev.zenith.provider.net.UrlNormalizer
import com.pilldev.zenith.providers.builtin.mapper.toProviderVideoSource
import com.pilldev.zenith.providers.builtin.parser.HdRezkaDecoder
import com.pilldev.zenith.providers.builtin.parser.HdRezkaParser

public open class HdRezkaProvider(
    private val hdRezkaParser: HdRezkaParser,
) : BaseZenithProvider(),
    MediaSourceProvider,
    PosterSourceProvider {
    override val metadata: ProviderMetadata =
        ProviderMetadata(
            id = BuiltInProviders.HDREZKA,
            name = "HDRezka",
            version = "1.0.0",
            capabilities = setOf(ProviderCapability.MEDIA_SOURCE, ProviderCapability.POSTER_SOURCE),
            description = "Качественный стриминг с популярными озвучками",
            pros = listOf("Высокое качество видео (1080p / 4K)"),
            cons = listOf("На большинстве зеркал требуется авторизация или подписка"),
            settingsSchema =
                listOf(
                    ProviderSettingSpec(
                        key = "mirror",
                        label = "Зеркало HDRezka",
                        defaultValue = "hdrezka.me",
                        description = "Домен или IP-адрес рабочего зеркала",
                    ),
                ),
            mirrors = com.pilldev.zenith.domain.model.StandardMirrors.HDREZKA
                .map { it.toProviderMirrorSpec() },
        )

    override suspend fun test(context: ProviderContext): ProviderTestResult =
        measureTest {
            val mirror = effectiveMirror().ifBlank { context.getSetting("mirror") ?: "hdrezka.me" }
            val html = hdRezkaParser.hdRezkaApi.search("Horimiya")
            if (html.isNotBlank()) {
                "Зеркало $mirror отвечает нормально"
            } else {
                error("Пустой ответ от зеркала $mirror")
            }
        }

    override suspend fun getSources(
        shikimoriId: Int,
        animeName: String,
        russianName: String?,
    ): ProviderResult<List<ProviderVideoSource>> =
        ProviderResult.of {
            val result = hdRezkaParser.getSources(shikimoriId, animeName, russianName)
            result.sources.map { it.toProviderVideoSource() }
        }

    override suspend fun getPoster(
        animeId: Int,
        animeName: String,
    ): ProviderResult<String?> =
        ProviderResult.of {
            val cleanName = AnimeTitleMatcher.cleanForSearch(animeName)
            val html = hdRezkaParser.hdRezkaApi.search(cleanName)
            val doc = Ksoup.parse(html)
            val img = doc.select(".b-content__inline_item img").firstOrNull()
                ?: doc.select(".b-sidecover img").firstOrNull()
            val src = img?.attr("src")?.takeIf { it.isNotBlank() } ?: img?.attr("data-src")
            src?.let { UrlNormalizer.resolve(it) }
        }

    override suspend fun resolveStream(
        episode: ProviderEpisode,
    ): ProviderResult<List<ProviderMediaStream>> =
        ProviderResult.of {
            val stream = hdRezkaParser.resolve(episode.url) ?: error("HDRezka resolve failed")
            if (stream.qualities.isNotEmpty()) {
                stream.qualities
                    .filter { !HdRezkaDecoder.isPremiumQuality(it.key) }
                    .map { (q, u) ->
                        ProviderMediaStream(
                            url = u,
                            quality = q,
                            isHls = u.contains(".m3u8"),
                        )
                    }.sortedByDescending { HdRezkaDecoder.getQualityWeight(it.quality ?: "") }
            } else {
                listOf(
                    ProviderMediaStream(
                        url = stream.url,
                        quality = stream.resolution,
                        isHls = stream.url.contains(".m3u8"),
                    ),
                )
            }
        }
}
