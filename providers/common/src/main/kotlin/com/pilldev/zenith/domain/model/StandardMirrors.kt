package com.pilldev.zenith.domain.model

public data class MirrorInfo(
    val url: String,
    val displayName: String,
    val isOfficial: Boolean = false,
    val description: String? = null,
)

public object StandardMirrors {
    public val SHIKIMORI: List<MirrorInfo> =
        listOf(
            MirrorInfo(
                url = "https://shikimori.io/",
                displayName = "shikimori.io",
                isOfficial = true,
                description = "Основное официальное зеркало (REST + GraphQL)",
            ),
            MirrorInfo(
                url = "https://shikimori.fi/",
                displayName = "shikimori.fi",
                isOfficial = false,
                description = "Резервное европейское зеркало",
            ),
            MirrorInfo(
                url = "https://ongaku.one/",
                displayName = "ongaku.one",
                isOfficial = false,
                description = "Альтернативное зеркало без цензуры",
            ),
        )

    public val KODIK: List<MirrorInfo> =
        listOf(
            MirrorInfo(
                url = "https://kodik-api.com/",
                displayName = "kodik-api.com",
                isOfficial = true,
                description = "Основной API-шлюз видеобалансера",
            ),
            MirrorInfo(
                url = "https://kodikplayer.com/",
                displayName = "kodikplayer.com",
                isOfficial = false,
                description = "Официальное веб-зеркало плеера",
            ),
            MirrorInfo(
                url = "https://kodik.info/",
                displayName = "kodik.info",
                isOfficial = false,
                description = "Резервное CDN-зеркало плеера",
            ),
        )

    public val ANI_LIBRIA: List<MirrorInfo> =
        listOf(
            MirrorInfo(
                url = "https://anilibria.top/api/v1/",
                displayName = "anilibria.top",
                isOfficial = true,
                description = "Официальная поддерживаемая ветка API v1",
            ),
            MirrorInfo(
                url = "https://api.anilibria.top/api/v1/",
                displayName = "api.anilibria.top",
                isOfficial = false,
                description = "Резервный шлюз API",
            ),
        )

    public val RUTOR: List<MirrorInfo> =
        listOf(
            MirrorInfo(
                url = "https://free-rutor.org",
                displayName = "free-rutor.org",
                isOfficial = true,
                description = "Рекомендуемое стабильное зеркало",
            ),
            MirrorInfo(
                url = "https://rutor.info",
                displayName = "rutor.info",
                isOfficial = false,
                description = "Основной домен Rutor",
            ),
            MirrorInfo(
                url = "https://rutor.is",
                displayName = "rutor.is",
                isOfficial = false,
                description = "Зеркало 2",
            ),
            MirrorInfo(
                url = "https://rutor.org",
                displayName = "rutor.org",
                isOfficial = false,
                description = "Зеркало 3",
            ),
        )

    public val HDREZKA: List<MirrorInfo> =
        listOf(
            MirrorInfo(
                url = "hdrezka.me",
                displayName = "hdrezka.me",
                isOfficial = true,
                description = "Основной домен каталога",
            ),
            MirrorInfo(
                url = "hdrezka.in",
                displayName = "hdrezka.in",
                isOfficial = false,
                description = "Зеркало 1 (стабильный CDN)",
            ),
            MirrorInfo(
                url = "rezka.ag",
                displayName = "rezka.ag",
                isOfficial = false,
                description = "Зеркало 2",
            ),
            MirrorInfo(
                url = "hdrezka-stream.net",
                displayName = "hdrezka-stream.net",
                isOfficial = false,
                description = "Зеркало 3",
            ),
        )

    public val YUMMY_ANIME: List<MirrorInfo> =
        listOf(
            MirrorInfo(
                url = "https://api.yani.tv/",
                displayName = "api.yani.tv",
                isOfficial = true,
                description = "Основной API бэкенд платформы",
            ),
            MirrorInfo(
                url = "https://yummyani.me/",
                displayName = "yummyani.me",
                isOfficial = false,
                description = "Официальный веб-хост",
            ),
        )

    public val LIFT: List<MirrorInfo> =
        listOf(
            MirrorInfo(
                url = "https://api.liftw.ws/",
                displayName = "api.liftw.ws",
                isOfficial = true,
                description = "Основной API-шлюз Lift",
            ),
            MirrorInfo(
                url = "https://api.embandr.ws/",
                displayName = "api.embandr.ws",
                isOfficial = false,
                description = "Резервный CDN-шлюз",
            ),
            MirrorInfo(
                url = "https://api.niteface.ws/",
                displayName = "api.niteface.ws",
                isOfficial = false,
                description = "Альтернативное зеркало",
            ),
            MirrorInfo(
                url = "https://api.lateremb.ws/",
                displayName = "api.lateremb.ws",
                isOfficial = false,
                description = "Резервное зеркало 2",
            ),
            MirrorInfo(
                url = "https://api.faphz.com/",
                displayName = "api.faphz.com",
                isOfficial = false,
                description = "Резервное зеркало 3",
            ),
            MirrorInfo(
                url = "https://api.twemd.ws/",
                displayName = "api.twemd.ws",
                isOfficial = false,
                description = "Резервное зеркало 4",
            ),
        )

    public val ANIME_GO: List<MirrorInfo> =
        listOf(
            MirrorInfo(
                url = "https://animego.me/",
                displayName = "animego.me",
                isOfficial = true,
                description = "Основной домен каталога",
            ),
            MirrorInfo(
                url = "https://animego.org/",
                displayName = "animego.org",
                isOfficial = false,
                description = "Резервное зеркало",
            ),
        )

    public val RUTRACKER: List<MirrorInfo> =
        listOf(
            MirrorInfo(
                url = "https://rutracker.org",
                displayName = "rutracker.org",
                isOfficial = true,
                description = "Основной домен трекера",
            ),
            MirrorInfo(
                url = "https://rutracker.net",
                displayName = "rutracker.net",
                isOfficial = false,
                description = "Зеркало 1",
            ),
            MirrorInfo(
                url = "https://rutracker.nl",
                displayName = "rutracker.nl",
                isOfficial = false,
                description = "Зеркало 2",
            ),
        )

    public val NYAA: List<MirrorInfo> =
        listOf(
            MirrorInfo(
                url = "https://nyaa.si",
                displayName = "nyaa.si",
                isOfficial = true,
                description = "Основной международный домен",
            ),
            MirrorInfo(
                url = "https://nyaa.land",
                displayName = "nyaa.land",
                isOfficial = false,
                description = "Зеркало 1",
            ),
            MirrorInfo(
                url = "https://nyaa.net",
                displayName = "nyaa.net",
                isOfficial = false,
                description = "Зеркало 2",
            ),
        )

    public fun getForProvider(providerId: String): List<MirrorInfo> =
        when (providerId) {
            "HDRezka" -> HDREZKA
            "Lift" -> LIFT
            "AnimeGO" -> ANIME_GO
            "Kodik" -> KODIK
            "AniLiberty", "AniLibria", "anilibria_tracker" -> ANI_LIBRIA
            "Torrent_RuTor", "RuTor", "rutor" -> RUTOR
            "RuTracker", "rutracker" -> RUTRACKER
            "Nyaa", "Nyaa.si", "nyaa" -> NYAA
            "Shikimori" -> SHIKIMORI
            "YummyAnime" -> YUMMY_ANIME
            else -> emptyList()
        }
}

public fun MirrorInfo.toProviderMirrorSpec(): com.pilldev.zenith.provider.model.ProviderMirrorSpec =
    com.pilldev.zenith.provider.model.ProviderMirrorSpec(
        url = url,
        displayName = displayName,
        isOfficial = isOfficial,
        description = description,
    )

public fun com.pilldev.zenith.provider.model.ProviderMirrorSpec.toMirrorInfo(): MirrorInfo =
    MirrorInfo(
        url = url,
        displayName = displayName,
        isOfficial = isOfficial,
        description = description,
    )
