package com.pilldev.zenith.providers.builtin.parser

import com.pilldev.zenith.domain.model.VideoSource

data class ParserResult(
    val sources: List<VideoSource>,
    val hasPartialFailures: Boolean = false,
)

interface AnimeParser {
    val name: String

    suspend fun getSources(
        shikimoriId: Int,
        animeName: String,
        russianName: String?,
    ): ParserResult
}
