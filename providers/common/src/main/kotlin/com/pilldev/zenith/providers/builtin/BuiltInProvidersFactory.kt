package com.pilldev.zenith.providers.builtin

import com.pilldev.zenith.domain.repository.AppDispatchers
import com.pilldev.zenith.domain.repository.PlayerSettingsRepository
import com.pilldev.zenith.provider.StreamExtractor
import com.pilldev.zenith.provider.ZenithProvider
import com.pilldev.zenith.providers.builtin.api.AniLibertyApi
import com.pilldev.zenith.providers.builtin.api.AniSkipApi
import com.pilldev.zenith.providers.builtin.api.AnimeSkipApi
import com.pilldev.zenith.providers.builtin.api.AnitypeApi
import com.pilldev.zenith.providers.builtin.api.HdRezkaApi
import com.pilldev.zenith.providers.builtin.api.KodikApi
import com.pilldev.zenith.providers.builtin.api.LiftApi
import com.pilldev.zenith.providers.builtin.api.YummyAnimeApi
import com.pilldev.zenith.providers.builtin.extractor.AksorStreamExtractor
import com.pilldev.zenith.providers.builtin.extractor.AniBoomStreamExtractor
import com.pilldev.zenith.providers.builtin.extractor.CvhStreamExtractor
import com.pilldev.zenith.providers.builtin.extractor.KodikStreamExtractor
import com.pilldev.zenith.providers.builtin.extractor.SibnetStreamExtractor
import com.pilldev.zenith.providers.builtin.parser.AniBoomExtractor
import com.pilldev.zenith.providers.builtin.parser.AniLibriaParser
import com.pilldev.zenith.providers.builtin.parser.AnimeGoParser
import com.pilldev.zenith.providers.builtin.parser.AnitypeParser
import com.pilldev.zenith.providers.builtin.parser.HdRezkaParser
import com.pilldev.zenith.providers.builtin.parser.KodikParser
import com.pilldev.zenith.providers.builtin.parser.LiftParser
import com.pilldev.zenith.providers.builtin.parser.YummyAnimeParser
import com.pilldev.zenith.providers.builtin.provider.AniLibertyProvider
import com.pilldev.zenith.providers.builtin.provider.AniLibriaTracker
import com.pilldev.zenith.providers.builtin.provider.AniSkipProvider
import com.pilldev.zenith.providers.builtin.provider.AnimeGoProvider
import com.pilldev.zenith.providers.builtin.provider.AnimeSkipProvider
import com.pilldev.zenith.providers.builtin.provider.AnitypeProvider
import com.pilldev.zenith.providers.builtin.provider.HdRezkaProvider
import com.pilldev.zenith.providers.builtin.provider.KodikProvider
import com.pilldev.zenith.providers.builtin.provider.LiftProvider
import com.pilldev.zenith.providers.builtin.provider.NyaaTracker
import com.pilldev.zenith.providers.builtin.provider.OpenSubtitlesProvider
import com.pilldev.zenith.providers.builtin.provider.RuTorTracker
import com.pilldev.zenith.providers.builtin.provider.RuTrackerTracker
import com.pilldev.zenith.providers.builtin.provider.TheIntroDbProvider
import com.pilldev.zenith.providers.builtin.provider.YummyAnimeProvider
import com.pilldev.zenith.providers.builtin.resolver.AksorResolver
import com.pilldev.zenith.providers.builtin.resolver.CvhResolver
import com.pilldev.zenith.providers.builtin.resolver.KodikResolver
import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json

public object BuiltInProvidersFactory {
    public fun createBuiltInExtractors(
        httpClient: HttpClient = HttpClient(),
        json: Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        },
    ): List<StreamExtractor> {
        val kodikResolver = KodikResolver(httpClient, json)
        val aksorResolver = AksorResolver(httpClient, json)
        val cvhResolver = CvhResolver(httpClient, json)
        val aniBoomExtractor = AniBoomExtractor(httpClient, json)

        return listOf(
            SibnetStreamExtractor(httpClient),
            AniBoomStreamExtractor(aniBoomExtractor),
            KodikStreamExtractor(kodikResolver),
            AksorStreamExtractor(aksorResolver),
            CvhStreamExtractor(cvhResolver),
        )
    }

    public fun createBuiltInProviders(
        httpClient: HttpClient,
        appDispatchers: AppDispatchers,
        playerSettings: PlayerSettingsRepository,
        json: Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        },
    ): List<ZenithProvider> {
        val kodikApi = KodikApi(httpClient)
        val hdRezkaApi = HdRezkaApi(httpClient, playerSettings)
        val aniLibertyApi = AniLibertyApi(httpClient)
        val yummyAnimeApi = YummyAnimeApi(httpClient, playerSettings)
        val anitypeApi = AnitypeApi(httpClient)
        val liftApi = LiftApi(httpClient, playerSettings)
        val aniSkipApi = AniSkipApi(httpClient)
        val animeSkipApi = AnimeSkipApi(httpClient, playerSettings)

        val aniBoomExtractor = AniBoomExtractor(httpClient, json)
        val kodikResolver = KodikResolver(httpClient, json)

        val kodikParser = KodikParser(kodikApi, json, playerSettings, appDispatchers)
        val hdRezkaParser = HdRezkaParser(hdRezkaApi, json)
        val aniLibriaParser = AniLibriaParser(aniLibertyApi, json, appDispatchers)
        val yummyAnimeParser = YummyAnimeParser(yummyAnimeApi, json, playerSettings, appDispatchers)
        val anitypeParser = AnitypeParser(httpClient, json, playerSettings, appDispatchers)
        val liftParser = LiftParser(liftApi)
        val animeGoParser = AnimeGoParser(httpClient, json, appDispatchers, aniBoomExtractor)

        val liftSubtitleManager = com.pilldev.zenith.providers.builtin.subtitles
            .LiftSubtitleManager(liftParser, liftApi, appDispatchers)
        val openSubtitlesManager = com.pilldev.zenith.providers.builtin.subtitles
            .OpenSubtitlesManager(httpClient, appDispatchers)

        val providers = mutableListOf<ZenithProvider>(
            KodikProvider(kodikParser, kodikResolver),
            HdRezkaProvider(hdRezkaParser),
            AniLibertyProvider(aniLibriaParser),
            YummyAnimeProvider(yummyAnimeParser),
            AnitypeProvider(anitypeParser),
            LiftProvider(liftParser, liftSubtitleManager),
            OpenSubtitlesProvider(openSubtitlesManager),
            AnimeGoProvider(animeGoParser),
            AniSkipProvider(aniSkipApi),
            AnimeSkipProvider(animeSkipApi, httpClient),
            TheIntroDbProvider(httpClient),
            AniLibriaTracker(aniLibertyApi, json, appDispatchers),
            NyaaTracker(httpClient, appDispatchers),
            RuTorTracker(playerSettings, httpClient),
            RuTrackerTracker(playerSettings, httpClient),
        )

        return providers
    }
}
