package com.pilldev.zenith.providers.builtin.provider

import com.pilldev.zenith.domain.model.StandardMirrors
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
import com.pilldev.zenith.provider.net.BrowserHeaders
import com.pilldev.zenith.providers.builtin.mapper.toProviderVideoSource
import com.pilldev.zenith.providers.builtin.parser.AnimeGoParser

public open class AnimeGoProvider(
    private var animeGoParser: AnimeGoParser? = null,
) : BaseZenithProvider(),
    MediaSourceProvider {

    constructor() : this(null)

    private fun ensureParser(): AnimeGoParser {
        val existing = animeGoParser
        if (existing != null) return existing
        val ctx = context
        val client = ctx?.httpClient ?: io.ktor.client.HttpClient()
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }
        val aniBoom = com.pilldev.zenith.providers.builtin.parser.AniBoomExtractor(client, json)
        val parser = AnimeGoParser(
            httpClient = client,
            json = json,
            appDispatchers = object : com.pilldev.zenith.domain.repository.AppDispatchers {
                override val main = kotlinx.coroutines.Dispatchers.Main
                override val io = kotlinx.coroutines.Dispatchers.IO
                override val default = kotlinx.coroutines.Dispatchers.Default
                override val unconfined = kotlinx.coroutines.Dispatchers.Unconfined
            },
            aniBoomExtractor = aniBoom,
        )
        animeGoParser = parser
        return parser
    }

    override val metadata: ProviderMetadata =
        ProviderMetadata(
            id = BuiltInProviders.ANIMEGO,
            name = "AnimeGO",
            version = "1.0.0",
            capabilities = setOf(ProviderCapability.MEDIA_SOURCE),
            description = "Популярный аниме-портал с обширной базой озвучек",
            pros = listOf("Быстрый плеер", "Большой выбор озвучек"),
            cons = listOf("Периодическая смена доменов"),
            settingsSchema =
                listOf(
                    ProviderSettingSpec(
                        key = "base_url",
                        label = "Базовый адрес AnimeGO",
                        defaultValue = "https://animego.me/",
                        description = "API endpoint или зеркало",
                    ),
                ),
            mirrors = StandardMirrors.ANIME_GO.map { it.toProviderMirrorSpec() },
        )

    override suspend fun test(context: ProviderContext): ProviderTestResult =
        measureTest {
            val result = ensureParser().getSources(54856, "Horimiya: Piece", "Хоримия: Кусочек")
            "AnimeGO активен. Найдено озвучек: ${result.sources.size}"
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
        ProviderResult.of {
            val streams = ensureParser().resolveStream(episode.url)
            streams.ifEmpty {
                listOf(
                    ProviderMediaStream(
                        url = episode.url,
                        quality = "Auto",
                        headers = BrowserHeaders.standardHeaders(),
                        isHls = episode.url.contains(".m3u8"),
                    ),
                )
            }
        }
}

public class AnimeGoProviderFactory : com.pilldev.zenith.provider.ZenithProviderFactory {
    override fun create(manifest: com.pilldev.zenith.provider.model.PluginManifest): com.pilldev.zenith.provider.ZenithProvider =
        AnimeGoProvider()
}
