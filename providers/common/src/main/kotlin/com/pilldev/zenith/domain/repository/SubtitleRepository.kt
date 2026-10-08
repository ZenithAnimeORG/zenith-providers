package com.pilldev.zenith.domain.repository

import com.pilldev.zenith.domain.model.ExternalSubtitle

public interface SubtitleRepository {
    public suspend fun searchSubtitles(
        query: String,
        languages: String = "ru,en",
        episodeNumber: Int? = null,
        seasonNumber: Int? = null,
    ): List<ExternalSubtitle>

    public suspend fun getDownloadLink(fileId: Long): String?
}
