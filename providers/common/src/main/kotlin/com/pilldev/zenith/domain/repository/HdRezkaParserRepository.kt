package com.pilldev.zenith.domain.repository

import com.pilldev.zenith.domain.model.HdRezkaStream
import com.pilldev.zenith.domain.model.VideoSource

interface HdRezkaParserRepository {
    suspend fun getHdRezkaSources(
        shikimoriId: Int,
        animeName: String,
        russianName: String?,
    ): List<VideoSource>

    suspend fun resolve(url: String): HdRezkaStream?

    suspend fun resolveForMirror(
        url: String,
        mirror: String,
    ): HdRezkaStream?

    suspend fun loginToMirror(
        mirror: String,
        login: String,
        password: String,
    ): Boolean

    suspend fun verifyStreamUrl(
        url: String,
        mirror: String,
    ): Boolean
}
