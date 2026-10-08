package com.pilldev.zenith.providers.builtin.parser

import co.touchlab.kermit.Logger
import com.pilldev.zenith.domain.model.EpisodeSource
import com.pilldev.zenith.domain.model.TranslationType
import com.pilldev.zenith.domain.model.VideoSource
import com.pilldev.zenith.domain.repository.AppDispatchers
import com.pilldev.zenith.provider.model.BuiltInProviders
import com.pilldev.zenith.providers.builtin.api.ktorfit.CollapsEmbedResponseDto
import com.pilldev.zenith.providers.builtin.api.ktorfit.CollapsKtorfitApi
import kotlinx.coroutines.withContext

public class CollapsParser
    constructor(
        private val collapsApi: CollapsKtorfitApi,
        private val appDispatchers: AppDispatchers,
    ) : AnimeParser {
        override val name: String = "Collaps"

        override suspend fun getSources(
            shikimoriId: Int,
            animeName: String,
            russianName: String?,
        ): ParserResult =
            withContext(appDispatchers.io) {
                Logger.withTag("CollapsParser").d { "Getting sources for $animeName / $russianName" }
                val sources = mutableListOf<VideoSource>()

                try {
                    val response = collapsApi.getEmbedByKp(kinopoiskId = shikimoriId)
                    processResponse(response, sources)
                } catch (e: Throwable) {
                    Logger.withTag("CollapsParser").e(e) { "Failed to fetch Collaps sources" }
                }

                ParserResult(sources = sources, hasPartialFailures = false)
            }

        private fun processResponse(
            response: CollapsEmbedResponseDto,
            out: MutableList<VideoSource>
        ) {
            val seasons = response.seasons
            if (!seasons.isNullOrEmpty()) {
                for (season in seasons) {
                    val episodes = season.episodes.orEmpty()
                    val episodeList = mutableListOf<EpisodeSource>()
                    for (ep in episodes) {
                        val num = ep.episode ?: 1
                        val url = ep.hlsUrl?.ifBlank { null } ?: ep.iframeUrl.orEmpty()
                        if (url.isNotBlank()) {
                            episodeList.add(EpisodeSource(number = num, url = url))
                        }
                    }
                    if (episodeList.isNotEmpty()) {
                        out.add(
                            VideoSource(
                                name = "Collaps",
                                translationName = "Collaps",
                                translationType = TranslationType.VO,
                                providerId = BuiltInProviders.COLLAPS,
                                episodes = episodeList.sortedBy { it.number },
                            ),
                        )
                    }
                }
            } else {
                val iframe = response.iframeUrl.orEmpty()
                if (iframe.isNotBlank()) {
                    out.add(
                        VideoSource(
                            name = "Collaps",
                            translationName = "Collaps",
                            translationType = TranslationType.VO,
                            providerId = BuiltInProviders.COLLAPS,
                            episodes = listOf(EpisodeSource(number = 1, url = iframe)),
                        ),
                    )
                }
            }
        }
    }
