package com.pilldev.zenith.domain.model

import kotlinx.serialization.Serializable
import com.pilldev.zenith.provider.matcher.TorrentTitleParser as SdkTorrentTitleParser

@Serializable
public object TorrentTitleParser {
    @Serializable
    public enum class TagType {
        RESOLUTION,
        QUALITY,
        AUDIO,
        GROUP,
        CODEC,
        OTHER,
    }

    @Serializable
    public data class Tag(
        val name: String,
        val type: TagType,
    )

    public data class ParsedTitle(
        val cleanTitle: String,
        val tags: List<Tag>,
        val season: Int? = null,
        val episode: Int? = null,
        val episodeEnd: Int? = null,
        val isBatch: Boolean = false,
        val releaseGroup: String? = null,
        val resolution: String? = null,
    ) {
        public val normalizedQuality: String
            get() = resolution?.lowercase() ?: "1080p"
    }

    public fun parseByteSize(sizeString: String): Long = SdkTorrentTitleParser.parseByteSize(sizeString)

    public fun qualityScore(quality: String): Int = SdkTorrentTitleParser.qualityScore(quality)

    public val DEFAULT_ANNOUNCE_TRACKERS: List<String> = SdkTorrentTitleParser.DEFAULT_ANNOUNCE_TRACKERS

    public fun buildMagnetUri(
        infoHash: String,
        displayName: String? = null,
        trackers: List<String> = DEFAULT_ANNOUNCE_TRACKERS,
    ): String = SdkTorrentTitleParser.buildMagnetUri(infoHash, displayName, trackers)

    public fun parse(title: String): ParsedTitle {
        val res = SdkTorrentTitleParser.parse(title)
        return ParsedTitle(
            cleanTitle = res.cleanTitle,
            tags = res.tags.map { Tag(it.name, TagType.valueOf(it.type.name)) },
            season = res.season,
            episode = res.episode,
            episodeEnd = res.episodeEnd,
            isBatch = res.isBatch,
            releaseGroup = res.releaseGroup,
            resolution = res.resolution,
        )
    }
}

public fun SdkTorrentTitleParser.Tag.toDomainTag(): TorrentTitleParser.Tag = TorrentTitleParser.Tag(name, TorrentTitleParser.TagType.valueOf(type.name))

public fun TorrentTitleParser.Tag.toSdkTag(): SdkTorrentTitleParser.Tag = SdkTorrentTitleParser.Tag(name, SdkTorrentTitleParser.TagType.valueOf(type.name))
