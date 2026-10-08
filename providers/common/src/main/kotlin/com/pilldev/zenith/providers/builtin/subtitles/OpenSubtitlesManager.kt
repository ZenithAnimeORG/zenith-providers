package com.pilldev.zenith.providers.builtin.subtitles

import co.touchlab.kermit.Logger
import com.pilldev.zenith.domain.model.ExternalSubtitle
import com.pilldev.zenith.domain.repository.AppDispatchers
import com.pilldev.zenith.domain.repository.SubtitleRepository
import com.pilldev.zenith.providers.builtin.api.ktorfit.OpenSubtitlesDownloadRequestDto
import com.pilldev.zenith.providers.builtin.api.ktorfit.OpenSubtitlesKtorfitApi
import com.pilldev.zenith.providers.builtin.api.ktorfit.createOpenSubtitlesKtorfitApi
import com.pilldev.zenith.providers.builtin.net.BuiltInEndpoints
import de.jensklingenberg.ktorfit.Ktorfit
import io.ktor.client.HttpClient
import kotlinx.coroutines.withContext

public typealias ExternalSubtitleInfo = ExternalSubtitle

public class OpenSubtitlesManager
    constructor(
        private val openSubtitlesApi: OpenSubtitlesKtorfitApi,
        private val appDispatchers: AppDispatchers,
    ) : SubtitleRepository {
        constructor(
            client: HttpClient,
            appDispatchers: AppDispatchers,
        ) : this(
            Ktorfit
                .Builder()
                .httpClient(client)
                .baseUrl(BuiltInEndpoints.OPEN_SUBTITLES)
                .build()
                .createOpenSubtitlesKtorfitApi(),
            appDispatchers,
        )

        private val apiKey = "ZenithAppSubtitlesKey"
        private val userAgent = "ZenithApp v1.0"

        public override suspend fun searchSubtitles(
            query: String,
            languages: String,
            episodeNumber: Int?,
            seasonNumber: Int?,
        ): List<ExternalSubtitle> =
            withContext(appDispatchers.io) {
                try {
                    val response = openSubtitlesApi.searchSubtitles(
                        apiKey = apiKey,
                        userAgent = userAgent,
                        query = query,
                        languages = languages,
                        episodeNumber = episodeNumber,
                        seasonNumber = seasonNumber,
                    )
                    val items = response.data.orEmpty()
                    items.mapNotNull { item ->
                        val attr = item.attributes ?: return@mapNotNull null
                        val file = attr.files?.firstOrNull() ?: return@mapNotNull null
                        val fileId = file.fileId ?: return@mapNotNull null

                        ExternalSubtitle(
                            fileId = fileId,
                            language = attr.language ?: "en",
                            releaseName = attr.release ?: file.fileName ?: query,
                            directUrl = null,
                            provider = "OpenSubtitles",
                        )
                    }
                } catch (e: Exception) {
                    Logger.withTag("OpenSubtitlesManager").w(e) { "Failed to search subtitles for $query" }
                    emptyList()
                }
            }

        public override suspend fun getDownloadLink(fileId: Long): String? =
            withContext(appDispatchers.io) {
                try {
                    val response = openSubtitlesApi.requestDownload(
                        apiKey = apiKey,
                        userAgent = userAgent,
                        request = OpenSubtitlesDownloadRequestDto(fileId = fileId),
                    )
                    response.link
                } catch (e: Exception) {
                    Logger.withTag("OpenSubtitlesManager").w(e) { "Failed to get download link for $fileId" }
                    null
                }
            }
    }
