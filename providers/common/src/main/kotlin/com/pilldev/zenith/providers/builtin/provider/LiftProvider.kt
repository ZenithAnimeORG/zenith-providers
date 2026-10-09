package com.pilldev.zenith.providers.builtin.provider

import com.pilldev.zenith.domain.model.StandardMirrors
import com.pilldev.zenith.domain.model.toProviderMirrorSpec
import com.pilldev.zenith.provider.BaseZenithProvider
import com.pilldev.zenith.provider.MediaSourceProvider
import com.pilldev.zenith.provider.SubtitleSourceProvider
import com.pilldev.zenith.provider.ZenithProvider
import com.pilldev.zenith.provider.ZenithProviderFactory
import com.pilldev.zenith.provider.context.ProviderContext
import com.pilldev.zenith.provider.model.BuiltInProviders
import com.pilldev.zenith.provider.model.ProviderCapability
import com.pilldev.zenith.provider.model.ProviderEpisode
import com.pilldev.zenith.provider.model.ProviderMediaStream
import com.pilldev.zenith.provider.model.ProviderMetadata
import com.pilldev.zenith.provider.model.ProviderResult
import com.pilldev.zenith.provider.model.ProviderSettingSpec
import com.pilldev.zenith.provider.model.ProviderSubtitle
import com.pilldev.zenith.provider.model.ProviderTestResult
import com.pilldev.zenith.provider.model.ProviderVideoSource
import com.pilldev.zenith.provider.model.measureTest
import com.pilldev.zenith.provider.net.BrowserHeaders
import com.pilldev.zenith.providers.builtin.api.LiftApi
import com.pilldev.zenith.providers.builtin.mapper.toProviderSubtitle
import com.pilldev.zenith.providers.builtin.mapper.toProviderVideoSource
import com.pilldev.zenith.providers.builtin.parser.LiftParser
import com.pilldev.zenith.providers.builtin.subtitles.LiftSubtitleManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

public open class LiftProvider(
    private var liftParser: LiftParser? = null,
    private var liftSubtitleManager: LiftSubtitleManager? = null,
) : BaseZenithProvider(),
    MediaSourceProvider,
    SubtitleSourceProvider {
    public constructor() : this(null, null)

    private fun ensureComponents() {
        if (liftParser == null || liftSubtitleManager == null) {
            val api = LiftApi(providerContext = context)
            val parser = liftParser ?: LiftParser(api)
            liftParser = parser
            if (liftSubtitleManager == null) {
                liftSubtitleManager = LiftSubtitleManager(
                    parser,
                    api,
                    object : com.pilldev.zenith.domain.repository.AppDispatchers {
                        override val main: CoroutineDispatcher = Dispatchers.Main
                        override val io: CoroutineDispatcher = Dispatchers.IO
                        override val default: CoroutineDispatcher = Dispatchers.Default
                        override val unconfined: CoroutineDispatcher = Dispatchers.Unconfined
                    },
                )
            }
        }
    }

    override val metadata: ProviderMetadata =
        ProviderMetadata(
            id = BuiltInProviders.LIFT,
            name = "Lift",
            version = "1.0.0",
            capabilities = setOf(ProviderCapability.MEDIA_SOURCE, ProviderCapability.SUBTITLES),
            description = "Современный мультиаудио балансер и каталог",
            pros = listOf("Прямые HLS-потоки без рекламы", "Оригинальные дорожки и субтитры", "Высокое качество 1080p"),
            cons = listOf("Каталог ориентирован на популярные релизы"),
            settingsSchema =
                listOf(
                    ProviderSettingSpec(
                        key = "base_url",
                        label = "Базовый адрес Lift",
                        defaultValue = "https://api.liftw.ws/",
                        description = "API endpoint или зеркало",
                    ),
                ),
            mirrors = StandardMirrors.LIFT.map { it.toProviderMirrorSpec() },
        )

    override suspend fun test(context: ProviderContext): ProviderTestResult =
        measureTest {
            ensureComponents()
            val result = (liftParser ?: error("LiftParser not initialized")).getSources(54856, "Horimiya: Piece", "Хоримия: Кусочек")
            "Lift API активен. Найдено озвучек: ${result.sources.size}"
        }

    override suspend fun getSources(
        animeId: Int,
        animeName: String,
        russianName: String?,
    ): ProviderResult<List<ProviderVideoSource>> =
        ProviderResult.of {
            ensureComponents()
            val result = (liftParser ?: error("LiftParser not initialized")).getSources(animeId, animeName, russianName)
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
                    headers = BrowserHeaders.standardHeaders(),
                    isHls = episode.url.contains(".m3u8"),
                ),
            ),
        )

    override suspend fun searchSubtitles(
        query: String,
        languages: String,
        episodeNumber: Int?,
        seasonNumber: Int?,
    ): ProviderResult<List<ProviderSubtitle>> =
        ProviderResult.of {
            ensureComponents()
            val subs = (liftSubtitleManager ?: error("LiftSubtitleManager not initialized")).searchSubtitles(query, languages, episodeNumber, seasonNumber)
            subs.map { it.toProviderSubtitle() }
        }

    override suspend fun getDownloadLink(fileId: Long): ProviderResult<String?> =
        ProviderResult.of {
            ensureComponents()
            (liftSubtitleManager ?: error("LiftSubtitleManager not initialized")).getDownloadLink(fileId)
        }
}

public class LiftProviderFactory : ZenithProviderFactory {
    override fun create(manifest: com.pilldev.zenith.provider.model.PluginManifest): ZenithProvider = LiftProvider()
}
