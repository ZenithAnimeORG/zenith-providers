package com.pilldev.zenith.providers.builtin.provider

import com.pilldev.zenith.domain.model.toProviderMirrorSpec
import com.pilldev.zenith.domain.repository.AppDispatchers
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
import com.pilldev.zenith.providers.builtin.api.KodikApi
import com.pilldev.zenith.providers.builtin.mapper.toProviderVideoSource
import com.pilldev.zenith.providers.builtin.parser.KodikParser
import com.pilldev.zenith.providers.builtin.resolver.KodikResolver
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json

public open class KodikProvider(
    private var kodikParser: KodikParser? = null,
    private var kodikResolver: KodikResolver? = null,
) : BaseZenithProvider(),
    MediaSourceProvider {

    constructor() : this(null, null)

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
                        type = com.pilldev.zenith.provider.model.SettingType.PASSWORD,
                        description = "Оставьте пустым для использования встроенного токена",
                    ),
                ),
            mirrors = com.pilldev.zenith.domain.model.StandardMirrors.KODIK
                .map { it.toProviderMirrorSpec() },
        )

    private fun ensureParser(): KodikParser {
        val existing = kodikParser
        if (existing != null) return existing

        val ctx = context
        val client = ctx?.httpClient ?: HttpClient()
        val customToken = ctx?.getSetting("api_token")?.trim().orEmpty()
        val api = KodikApi(client)
        val parser = KodikParser(
            kodikApi = api,
            json = Json { ignoreUnknownKeys = true; isLenient = true },
            playerSettingsManager = null,
            appDispatchers = object : AppDispatchers {
                override val main = Dispatchers.Main
                override val io = Dispatchers.IO
                override val default = Dispatchers.Default
                override val unconfined = Dispatchers.Unconfined
            },
            overrideToken = customToken.ifBlank { null },
        )
        kodikParser = parser
        return parser
    }

    private fun ensureResolver(): KodikResolver {
        val existing = kodikResolver
        if (existing != null) return existing

        val ctx = context
        val client = ctx?.httpClient ?: HttpClient()
        val resolver = KodikResolver(client, Json { ignoreUnknownKeys = true; isLenient = true })
        kodikResolver = resolver
        return resolver
    }

    override suspend fun test(context: ProviderContext): ProviderTestResult =
        measureTest {
            val parser = ensureParser()
            val result = parser.getSources(54856, "Horimiya: Piece", "Хоримия: Кусочек")
            "Kodik API активен. Найдено озвучек: ${result.sources.size}"
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

    override suspend fun resolveStream(
        episode: ProviderEpisode,
    ): ProviderResult<List<ProviderMediaStream>> {
        val resolver = ensureResolver()
        val stream = resolver.resolve(episode.url)
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
        return super.resolveStream(episode)
    }
}

public class KodikProviderFactory : com.pilldev.zenith.provider.ZenithProviderFactory {
    override fun create(manifest: com.pilldev.zenith.provider.model.PluginManifest): com.pilldev.zenith.provider.ZenithProvider =
        KodikProvider()
}
