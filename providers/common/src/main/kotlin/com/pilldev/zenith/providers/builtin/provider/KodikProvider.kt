package com.pilldev.zenith.providers.builtin.provider

import com.pilldev.zenith.domain.model.toProviderMirrorSpec
import com.pilldev.zenith.provider.BaseZenithProvider
import com.pilldev.zenith.provider.MediaSourceProvider
import com.pilldev.zenith.provider.context.ProviderContext
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
import com.pilldev.zenith.providers.builtin.mapper.toProviderVideoSource
import com.pilldev.zenith.providers.builtin.parser.KodikParser
import com.pilldev.zenith.providers.builtin.resolver.KodikResolver

public open class KodikProvider(
    private val kodikParser: KodikParser,
    private val kodikResolver: KodikResolver? = null,
) : BaseZenithProvider(),
    MediaSourceProvider {
    override val metadata: ProviderMetadata =
        ProviderMetadata(
            id = BuiltInProviders.KODIK,
            name = "Kodik",
            version = "1.0.0",
            capabilities = setOf(ProviderCapability.MEDIA_SOURCE),
            description = "Основной агрегатор большинства озвучек и плееров",
            pros = listOf("Огромный выбор плееров и озвучек", "Быстрая загрузка потоков"),
            cons = listOf("Максимальное качество в 720p"),
            settingsSchema =
                listOf(
                    ProviderSettingSpec(
                        key = "api_token",
                        label = "API Токен Kodik",
                        description = "Оставьте пустым для использования встроенного токена",
                    ),
                ),
            mirrors = com.pilldev.zenith.domain.model.StandardMirrors.KODIK
                .map { it.toProviderMirrorSpec() },
        )

    override suspend fun test(context: ProviderContext): ProviderTestResult =
        measureTest {
            val result = kodikParser.getSources(54856, "Horimiya: Piece", "Хоримия: Кусочек")
            "Kodik API активен. Найдено озвучек: ${result.sources.size}"
        }

    override suspend fun getSources(
        shikimoriId: Int,
        animeName: String,
        russianName: String?,
    ): ProviderResult<List<ProviderVideoSource>> =
        ProviderResult.of {
            val result = kodikParser.getSources(shikimoriId, animeName, russianName)
            result.sources.map { it.toProviderVideoSource() }
        }

    override suspend fun resolveStream(
        episode: ProviderEpisode,
    ): ProviderResult<List<ProviderMediaStream>> {
        if (kodikResolver != null) {
            val stream = kodikResolver.resolve(episode.url)
            if (stream != null) {
                val list = stream.qualities
                    .map { (q, u) ->
                        ProviderMediaStream(
                            url = u,
                            quality = q,
                            headers = stream.headers,
                            isHls = u.contains(".m3u8"),
                        )
                    }.ifEmpty {
                        listOf(
                            ProviderMediaStream(
                                url = stream.url,
                                quality = stream.resolution,
                                headers = stream.headers,
                                isHls = stream.url.contains(".m3u8"),
                            ),
                        )
                    }
                return ProviderResult.Success(list)
            }
        }
        return super.resolveStream(episode)
    }
}
