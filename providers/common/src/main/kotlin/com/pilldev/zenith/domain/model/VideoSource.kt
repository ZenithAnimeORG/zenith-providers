package com.pilldev.zenith.domain.model

import com.pilldev.zenith.provider.matcher.TorrentTitleParser
import com.pilldev.zenith.provider.model.BuiltInProviders
import com.pilldev.zenith.provider.model.ProviderId
import com.pilldev.zenith.provider.model.ProviderTranslationType
import com.pilldev.zenith.provider.model.ProviderVideoSource
import kotlinx.serialization.Serializable

data class SourceFetchResult(
    val sources: List<VideoSource>,
    val hasFailures: Boolean,
)

@Serializable
enum class TranslationType {
    DUB,
    VO,
    SUB,
    UNKNOWN,
}

@Serializable
data class VideoSource(
    val name: String = "",
    val translationName: String = "",
    val translationType: TranslationType = TranslationType.VO,
    val providerId: ProviderId = BuiltInProviders.KODIK,
    val episodes: List<EpisodeSource> = emptyList(),
    val qualities: Map<String, String>? = null,
    val tracker: String? = null,
    val infoHash: String? = null,
    val size: Long? = null,
    val seeders: Int? = null,
    val tags: List<TorrentTitleParser.Tag> = emptyList(),
)

fun ProviderVideoSource.toVideoSource(providerId: ProviderId): VideoSource =
    VideoSource(
        name = name,
        translationName = translationName,
        translationType = when (translationType) {
            ProviderTranslationType.DUB -> TranslationType.DUB
            ProviderTranslationType.VO -> TranslationType.VO
            ProviderTranslationType.SUB -> TranslationType.SUB
            ProviderTranslationType.UNKNOWN -> TranslationType.UNKNOWN
        },
        providerId = providerId,
        episodes = episodes.map {
            EpisodeSource(
                number = it.number,
                url = it.url,
                quality = it.quality,
                providerId = it.providerId ?: providerId,
            )
        },
        qualities = qualities,
    )

@Serializable
data class EpisodeSource(
    val number: Int = 0,
    val url: String = "",
    val quality: Map<String, String>? = null,
    val providerId: ProviderId = BuiltInProviders.KODIK,
)

public fun parseQualityRank(quality: String?): Int {
    if (quality == null) return 0
    val lower = quality.lowercase().trim()
    return when {
        "2160" in lower || "4k" in lower -> 2160
        "1440" in lower || "2k" in lower -> 1440
        "1080" in lower -> 1080
        "720" in lower -> 720
        "480" in lower -> 480
        "360" in lower -> 360
        "auto" in lower -> 1
        else -> lower.filter { it.isDigit() }.toIntOrNull() ?: 0
    }
}
