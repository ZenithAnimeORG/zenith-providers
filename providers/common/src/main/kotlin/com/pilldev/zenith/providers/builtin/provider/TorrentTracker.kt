package com.pilldev.zenith.providers.builtin.provider

import com.pilldev.zenith.domain.model.TorrentTitleParser
import com.pilldev.zenith.domain.model.TranslationType
import com.pilldev.zenith.domain.model.toDomainTag
import com.pilldev.zenith.domain.model.toSdkTag
import com.pilldev.zenith.provider.TorrentSourceProvider
import com.pilldev.zenith.provider.model.ProviderResult
import com.pilldev.zenith.provider.model.ProviderTorrentSource
import com.pilldev.zenith.provider.model.ProviderTranslationType
import kotlinx.serialization.Serializable

@Serializable
public data class TorrentResult(
    val title: String,
    val infoHash: String? = null,
    val magnetUri: String? = null,
    val size: Long = 0,
    val seeders: Int = 0,
    val leechers: Int = 0,
    val quality: String = "1080p",
    val trackerName: String,
    val translationName: String = "",
    val translationType: TranslationType = TranslationType.VO,
    val tags: List<TorrentTitleParser.Tag> = emptyList(),
)

public fun ProviderTorrentSource.toTorrentResult(): TorrentResult =
    TorrentResult(
        title = title,
        infoHash = infoHash,
        magnetUri = magnetUri,
        size = size,
        seeders = seeders,
        leechers = leechers,
        quality = quality,
        trackerName = trackerName,
        translationName = translationName,
        translationType = when (translationType) {
            ProviderTranslationType.DUB -> TranslationType.DUB
            ProviderTranslationType.VO -> TranslationType.VO
            ProviderTranslationType.SUB -> TranslationType.SUB
            ProviderTranslationType.UNKNOWN -> TranslationType.UNKNOWN
        },
        tags = tags.map { it.toDomainTag() },
    )

public fun TorrentResult.toProviderTorrentSource(): ProviderTorrentSource =
    ProviderTorrentSource(
        title = title,
        infoHash = infoHash,
        magnetUri = magnetUri,
        size = size,
        seeders = seeders,
        leechers = leechers,
        quality = quality,
        trackerName = trackerName,
        translationName = translationName,
        translationType = when (translationType) {
            TranslationType.DUB -> ProviderTranslationType.DUB
            TranslationType.VO -> ProviderTranslationType.VO
            TranslationType.SUB -> ProviderTranslationType.SUB
            TranslationType.UNKNOWN -> ProviderTranslationType.UNKNOWN
        },
        tags = tags.map { it.toSdkTag() },
    )

public interface TorrentTracker : TorrentSourceProvider {
    public val name: String

    public suspend fun search(
        query: String,
        russianName: String? = null,
    ): List<TorrentResult>

    override suspend fun search(
        query: String,
        russianName: String?,
        shikimoriId: Int,
    ): ProviderResult<List<ProviderTorrentSource>> =
        try {
            ProviderResult.Success(search(query, russianName).map { it.toProviderTorrentSource() })
        } catch (e: Throwable) {
            ProviderResult.Failure(e.message ?: "Search failed", e)
        }
}

public class AuthExpiredException(
    trackerName: String,
) : Exception("Authentication expired for $trackerName")
