package com.pilldev.zenith.domain.repository
import com.pilldev.zenith.domain.model.KodikProvider
import com.pilldev.zenith.domain.model.PlayMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

public interface PlayerSettingsRepository : FeedbackSettingsRepository {
    public val playMode: StateFlow<PlayMode>
    public val skipType: StateFlow<String>
    public val skipProvider: StateFlow<String>
    public val skipFallbackOrder: StateFlow<List<String>>
    public val skipStep: StateFlow<Int>
    public val seekStep: StateFlow<Int>
    public val skipOpeningEnabled: StateFlow<Boolean>
    public val skipEndingEnabled: StateFlow<Boolean>
    public val posterProvider: StateFlow<String>
    public val posterFallbackOrder: StateFlow<List<String>>
    public val activePosters: StateFlow<Set<String>>
    public val sourceProvider: StateFlow<String>
    public val sourceFallbackOrder: StateFlow<List<String>>
    public val activeSources: StateFlow<Set<String>>
    public val activeSkipProviders: StateFlow<Set<String>>
    public val activeSubtitleProviders: StateFlow<Set<String>>
    public val subtitleFallbackOrder: StateFlow<List<String>>
    public val shikimoriBaseUrl: StateFlow<String>
    public val metadataProvider: StateFlow<String>
    public val kodikBaseUrl: StateFlow<String>
    public val aniLibriaBaseUrl: StateFlow<String>
    public val liftBaseUrl: StateFlow<String>
    public val liftAutoMirror: StateFlow<Boolean>
    public val liftRankedMirrors: StateFlow<List<String>>
    public val hdRezkaBaseUrl: StateFlow<String>
    public val hdRezkaAutoMirror: StateFlow<Boolean>
    public val hdRezkaRankedMirrors: StateFlow<List<String>>
    public val hdRezkaSessionId: StateFlow<String>
    public val hdRezkaDleUserId: StateFlow<String>
    public val hdRezkaDlePassword: StateFlow<String>
    public val hdRezkaLogin: StateFlow<String>
    public val hdRezkaPassword: StateFlow<String>
    public val hdRezkaMirrorsCookies: StateFlow<Map<String, String>>

    /** Map of dual session profiles (Guest + Auth) for HDRezka mirrors. */
    public val hdRezkaMirrorProfiles: StateFlow<Map<String, com.pilldev.zenith.domain.model.HdRezkaMirrorProfile>>

    /** Map of cached test results for HDRezka mirrors. */
    public val hdRezkaMirrorsTestResults: StateFlow<Map<String, String>>
    public val filterGarbagePlayers: StateFlow<Boolean>
    public val userAgent: StateFlow<String>
    public val isLegalAccepted: StateFlow<Boolean>
    public val maxRating: StateFlow<String>
    public val showHentai: StateFlow<Boolean>

    /**
     * Offline mode toggle.
     */
    public val isOfflineMode: StateFlow<Boolean>
    public val hideHentai: StateFlow<Boolean>
    public val isAdultConfirmed: StateFlow<Boolean>
    public val playerHorizontalOnly: StateFlow<Boolean>
    public val autoPipEnabled: StateFlow<Boolean>
    public val gestureBrightnessEnabled: StateFlow<Boolean>
    public val gestureVolumeEnabled: StateFlow<Boolean>
    public val gestureSeekEnabled: StateFlow<Boolean>
    public val gestureSeekDragEnabled: StateFlow<Boolean>
    public val gestureSeekDoubleTapEnabled: StateFlow<Boolean>
    public val gestureHoldRequired: StateFlow<Boolean>
    public val showSkipSegmentsOnTimeline: StateFlow<Boolean>
    public val preloadNextEpisode: StateFlow<Boolean>
    public val playbackSpeed: StateFlow<Float>
    public val defaultShaders: StateFlow<Set<String>>
    public val anime4kStrength: StateFlow<Float>
    public val hdrExposure: StateFlow<Float>
    public val hdrContrast: StateFlow<Float>

    /** Type of Anime4K enhancer. */
    public val anime4kType: StateFlow<String>

    /** Mode of Anime4K processing. */
    public val anime4kMode: StateFlow<String>

    /** Whether debanding is enabled. */
    public val debandingEnabled: StateFlow<Boolean>

    /** Selected decoder type. */
    public val decoderType: StateFlow<String>

    /** Video brightness level. */
    public val brightnessVal: StateFlow<Int>

    /** Video saturation level. */
    public val saturationVal: StateFlow<Int>

    /** Video contrast level. */
    public val contrastVal: StateFlow<Int>

    /** Video gamma level. */
    public val gammaVal: StateFlow<Int>

    /** Video hue level. */
    public val hueVal: StateFlow<Int>

    /** Video sharpness level. */
    public val sharpnessVal: StateFlow<Int>

    /** Selected color filter preset. */
    public val colorPreset: StateFlow<String>
    public val mpvHighQualityScaling: StateFlow<Boolean>
    public val mpvInterpolation: StateFlow<Boolean>
    public val mpvAntiRinging: StateFlow<Boolean>
    public val useGpuNext: StateFlow<Boolean>
    public val useVulkan: StateFlow<Boolean>
    public val volumeNormalization: StateFlow<Boolean>
    public val anime4kDarken: StateFlow<Boolean>
    public val anime4kThin: StateFlow<Boolean>
    public val anime4kDeblur: StateFlow<Boolean>
    public val krigBilateralEnabled: StateFlow<Boolean>
    public val casSharpenEnabled: StateFlow<Boolean>
    public val yummyAnimeInternalPlayers: StateFlow<Set<String>>
    public val proxyEnabled: StateFlow<Boolean>
    public val proxyHost: StateFlow<String>
    public val proxyPort: StateFlow<Int>
    public val proxyUsername: StateFlow<String>
    public val proxyPassword: StateFlow<String>
    public val proxyType: StateFlow<String>
    public val torrentTrackers: StateFlow<Set<String>>
    public val ruTrackerBaseUrl: StateFlow<String>
    public val ruTrackerSessionId: StateFlow<String>
    public val ruTrackerData: StateFlow<String>
    public val ruTrackerSsl: StateFlow<String>
    public val ruTrackerGuid: StateFlow<String>
    public val ruTorBaseUrl: StateFlow<String>
    public val maxCacheSize: StateFlow<Int>
    public val isFirstLaunch: StateFlow<Boolean>
    public val anitypeAccessToken: StateFlow<String>
    public val anitypeRefreshToken: StateFlow<String>
    public val anitypeRefreshTime: StateFlow<Long>
    public val anitypeUsername: StateFlow<String>
    public val anitypePassword: StateFlow<String>
    public val anitypeSub: StateFlow<Boolean>
    public val kodikPrimaryProvider: StateFlow<KodikProvider>
    public val subtitleFontScale: StateFlow<Float>
    public val subtitleDelay: StateFlow<Double>
    public val preferredSubtitleLanguage: StateFlow<String>
    public val defaultBookmarkTab: StateFlow<String>
    public val ambientModeEnabled: StateFlow<Boolean>
    public val ambientModeIntensity: StateFlow<Float>

    public fun triggerSync()

    public fun setAmbientModeEnabled(enabled: Boolean)

    public fun setAmbientModeIntensity(intensity: Float)

    public fun setDefaultBookmarkTab(tab: String)

    public fun setSkipType(type: String)

    public fun setSkipProvider(provider: String)

    public fun setSkipFallbackOrder(order: List<String>)

    public fun setSkipStep(seconds: Int)

    public fun setSeekStep(seconds: Int)

    public fun setSkipOpeningEnabled(enabled: Boolean)

    public fun setSkipEndingEnabled(enabled: Boolean)

    public fun setPosterProvider(provider: String)

    public fun setPosterFallbackOrder(order: List<String>)

    public fun setSourceProvider(provider: String)

    public fun setSourceFallbackOrder(order: List<String>)

    public fun toggleSource(
        source: String,
        active: Boolean
    )

    public fun togglePoster(
        poster: String,
        active: Boolean
    )

    public fun toggleSkip(
        provider: String,
        active: Boolean
    )

    public fun toggleSubtitleProvider(
        provider: String,
        active: Boolean
    )

    public fun setActivePosters(posters: Set<String>)

    public fun setActiveSkipProviders(providers: Set<String>)

    public fun setActiveSubtitleProviders(providers: Set<String>)

    public fun setSubtitleFallbackOrder(order: List<String>)

    public fun setShikimoriBaseUrl(url: String)

    public fun setMetadataProvider(provider: String)

    public fun setKodikBaseUrl(url: String)

    public fun setAniLibriaBaseUrl(url: String)

    public fun setLiftBaseUrl(url: String)

    public fun setLiftAutoMirror(enabled: Boolean)

    public fun setLiftRankedMirrors(mirrors: List<String>)

    public fun setHdRezkaBaseUrl(url: String)

    public fun setHdRezkaAutoMirror(enabled: Boolean)

    public fun setHdRezkaRankedMirrors(mirrors: List<String>)

    public fun setHdRezkaAuth(
        sessionId: String,
        dleUserId: String
    )

    public fun setHdRezkaAuth(
        sessionId: String,
        dleUserId: String,
        dlePassword: String
    )

    public fun setHdRezkaLogin(login: String)

    public fun setHdRezkaPassword(password: String)

    public fun setHdRezkaMirrorCookies(
        mirror: String,
        cookies: String
    )

    public fun clearHdRezkaMirrorCookies(mirror: String)

    /** Saves or updates the dual session profile for a specific HDRezka mirror. */
    public fun setHdRezkaMirrorProfile(
        mirror: String,
        profile: com.pilldev.zenith.domain.model.HdRezkaMirrorProfile
    )

    public fun clearHdRezkaMirrorProfile(mirror: String)

    /** Saves the test result for a specific HDRezka mirror. */
    public fun setHdRezkaMirrorTestResult(
        mirror: String,
        result: String
    )

    public fun setFilterGarbagePlayers(enabled: Boolean)

    public fun setUserAgent(ua: String)

    public fun setLegalAccepted(accepted: Boolean)

    public fun setMaxRating(rating: String)

    public fun setShowHentai(show: Boolean)

    public fun setHideHentai(hide: Boolean)

    public fun setAdultConfirmed(confirmed: Boolean)

    public fun setPlayerHorizontalOnly(enabled: Boolean)

    public fun setAutoPipEnabled(enabled: Boolean)

    public fun setGestureBrightnessEnabled(enabled: Boolean)

    public fun setGestureVolumeEnabled(enabled: Boolean)

    public fun setGestureSeekEnabled(enabled: Boolean)

    public fun setGestureSeekDragEnabled(enabled: Boolean)

    public fun setGestureSeekDoubleTapEnabled(enabled: Boolean)

    public fun setGestureHoldRequired(enabled: Boolean)

    public fun setShowSkipSegmentsOnTimeline(enabled: Boolean)

    public fun setPreloadNextEpisode(enabled: Boolean)

    public fun setPlaybackSpeed(speed: Float)

    public fun toggleDefaultShader(
        shader: String,
        active: Boolean,
    )

    public fun setAnime4kStrength(strength: Float)

    public fun setHdrExposure(exposure: Float)

    public fun setHdrContrast(contrast: Float)

    /** Sets the Anime4K type. */
    public fun setAnime4kType(value: String)

    /** Sets the Anime4K processing mode. */
    public fun setAnime4kMode(value: String)

    /** Enables/disables debanding. */
    public fun setDebandingEnabled(value: Boolean)

    /** Sets the video decoder type. */
    public fun setDecoderType(value: String)

    /** Sets the brightness value. */
    public fun setBrightnessVal(value: Int)

    /** Sets the saturation value. */
    public fun setSaturationVal(value: Int)

    /** Sets the contrast value. */
    public fun setContrastVal(value: Int)

    /** Sets the gamma value. */
    public fun setGammaVal(value: Int)

    /** Sets the hue value. */
    public fun setHueVal(value: Int)

    /** Sets the sharpness value. */
    public fun setSharpnessVal(value: Int)

    /** Sets the color preset. */
    public fun setColorPreset(value: String)

    public fun setMpvHighQualityScaling(value: Boolean)

    public fun setMpvInterpolation(value: Boolean)

    public fun setMpvAntiRinging(value: Boolean)

    public fun setUseGpuNext(value: Boolean)

    public fun setUseVulkan(value: Boolean)

    public fun setVolumeNormalization(value: Boolean)

    public fun setAnime4kDarken(value: Boolean)

    public fun setAnime4kThin(value: Boolean)

    public fun setAnime4kDeblur(value: Boolean)

    public fun setKrigBilateralEnabled(value: Boolean)

    public fun setCasSharpenEnabled(value: Boolean)

    public fun toggleYummyInternalPlayer(
        player: String,
        active: Boolean,
    )

    public fun setProxyEnabled(enabled: Boolean)

    public fun setProxyHost(host: String)

    public fun setProxyPort(port: Int)

    public fun setProxyCredentials(
        username: String,
        password: String,
    )

    public fun setProxyType(type: String)

    public fun toggleTorrentTracker(
        tracker: String,
        active: Boolean,
    )

    public fun setRuTrackerBaseUrl(url: String)

    public fun setRuTrackerSession(
        sessionId: String,
        data: String,
        ssl: String = "",
        guid: String = ""
    )

    public fun setRuTorBaseUrl(url: String)

    public fun setMaxCacheSize(gb: Int)

    public suspend fun savePosition(
        animeId: Int,
        episodeNumber: Int,
        position: Long
    )

    public suspend fun getPosition(
        animeId: Int,
        episodeNumber: Int
    ): Long

    public suspend fun saveDuration(
        animeId: Int,
        episodeNumber: Int,
        duration: Long
    )

    public suspend fun getDuration(
        animeId: Int,
        episodeNumber: Int
    ): Long

    public fun savePositionAsync(
        animeId: Int,
        episodeNumber: Int,
        position: Long
    )

    public fun savePositionAsync(
        animeId: Int,
        episodeNumber: Int,
        position: Long,
        duration: Long
    )

    public suspend fun getLastWatchedEpisode(animeId: Int): Int?

    public fun getAllWatchedPositions(animeId: Int): Flow<Map<Int, Long>>

    public fun getAllEpisodeDurations(animeId: Int): Flow<Map<Int, Long>>

    /**
     * Set offline mode active.
     */
    public fun setOfflineMode(active: Boolean)

    public fun setPlayMode(mode: PlayMode)

    public fun setFirstLaunch(isFirst: Boolean)

    public fun setActiveSources(sources: Set<String>)

    public val customKodikToken: StateFlow<String>
    public val customYummyPublicToken: StateFlow<String>
    public val customYummyPrivateToken: StateFlow<String>
    public val customAnimeSkipClientId: StateFlow<String>

    public val effectiveKodikToken: String
    public val effectiveYummyPublicToken: String
    public val effectiveYummyPrivateToken: String
    public val effectiveAnimeSkipClientId: String

    public fun setCustomKodikToken(token: String)

    public fun setCustomYummyPublicToken(token: String)

    public fun setCustomYummyPrivateToken(token: String)

    public fun setCustomAnimeSkipClientId(clientId: String)

    public fun setAnitypeAuth(
        accessToken: String,
        refreshToken: String,
        refreshTime: Long,
        username: String,
        password: String,
        sub: Boolean = false,
    )

    public fun setAnitypeSub(sub: Boolean)

    public fun clearAnitypeAuth()

    public fun setKodikPrimaryProvider(provider: KodikProvider)

    public fun setSubtitleFontScale(scale: Float)

    public fun setSubtitleDelay(delay: Double)

    public fun setPreferredSubtitleLanguage(lang: String)

    public fun isFirstLaunchFlow(): Flow<Boolean>
}
