package com.pilldev.zenith.providers.builtin.parser

import co.touchlab.kermit.Logger
import com.pilldev.zenith.domain.model.EpisodeSource
import com.pilldev.zenith.domain.model.TranslationType
import com.pilldev.zenith.domain.model.VideoSource
import com.pilldev.zenith.domain.repository.AppDispatchers
import com.pilldev.zenith.domain.repository.PlayerSettingsRepository
import com.pilldev.zenith.provider.model.BuiltInProviders
import com.pilldev.zenith.providers.builtin.api.ktorfit.VideoCdnKtorfitApi
import com.pilldev.zenith.providers.builtin.api.ktorfit.VideoCdnShortResponseDto
import kotlinx.coroutines.withContext

public class VideoCdnParser
    constructor(
        private val videoCdnApi: VideoCdnKtorfitApi,
        private val playerSettingsManager: PlayerSettingsRepository? = null,
        private val appDispatchers: AppDispatchers,
    ) : AnimeParser {
        override val name: String = "VideoCDN"

        override suspend fun getSources(
            shikimoriId: Int,
            animeName: String,
            russianName: String?,
        ): ParserResult =
            withContext(appDispatchers.io) {
                Logger.withTag("VideoCdnParser").d { "Getting sources for $animeName / $russianName" }
                val token = playerSettingsManager?.customKodikToken?.value?.ifBlank { null } ?: "free"
                val sources = mutableListOf<VideoSource>()

                try {
                    val searchName = russianName?.takeIf(String::isNotBlank) ?: animeName
                    val response = videoCdnApi.searchShort(apiToken = token, title = searchName)
                    processShortResponse(response, sources)
                } catch (e: Throwable) {
                    Logger.withTag("VideoCdnParser").e(e) { "Failed to fetch VideoCDN sources" }
                }

                ParserResult(sources = sources, hasPartialFailures = false)
            }

        private fun processShortResponse(
            response: VideoCdnShortResponseDto,
            out: MutableList<VideoSource>
        ) {
            val items = response.data ?: return
            for (item in items) {
                val iframe = item.iframeSrc.orEmpty()
                if (iframe.isBlank()) continue

                val translations = item.translations.orEmpty()
                if (translations.isEmpty()) {
                    out.add(
                        VideoSource(
                            name = "VideoCDN",
                            translationName = item.title.orEmpty(),
                            translationType = TranslationType.VO,
                            providerId = BuiltInProviders.VIDEOCDN,
                            episodes = listOf(EpisodeSource(number = 1, url = iframe)),
                        ),
                    )
                } else {
                    for (tr in translations) {
                        val trUrl = tr.iframeSrc?.ifBlank { null } ?: iframe
                        val epCount = tr.episodesCount ?: 1
                        val episodes = (1..epCount).map { EpisodeSource(number = it, url = trUrl) }
                        out.add(
                            VideoSource(
                                name = "VideoCDN",
                                translationName = tr.title ?: item.title.orEmpty(),
                                translationType = TranslationType.VO,
                                providerId = BuiltInProviders.VIDEOCDN,
                                episodes = episodes,
                            ),
                        )
                    }
                }
            }
        }
    }
