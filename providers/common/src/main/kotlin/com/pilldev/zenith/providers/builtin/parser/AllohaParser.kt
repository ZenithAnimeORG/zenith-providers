package com.pilldev.zenith.providers.builtin.parser

import co.touchlab.kermit.Logger
import com.pilldev.zenith.domain.model.EpisodeSource
import com.pilldev.zenith.domain.model.TranslationType
import com.pilldev.zenith.domain.model.VideoSource
import com.pilldev.zenith.domain.repository.AppDispatchers
import com.pilldev.zenith.domain.repository.PlayerSettingsRepository
import com.pilldev.zenith.provider.model.BuiltInProviders
import com.pilldev.zenith.providers.builtin.api.ktorfit.AllohaKtorfitApi
import com.pilldev.zenith.providers.builtin.api.ktorfit.AllohaResponseDto
import kotlinx.coroutines.withContext

public class AllohaParser
    constructor(
        private val allohaApi: AllohaKtorfitApi,
        private val playerSettingsManager: PlayerSettingsRepository? = null,
        private val appDispatchers: AppDispatchers,
    ) : AnimeParser {
        override val name: String = "Alloha"

        override suspend fun getSources(
            shikimoriId: Int,
            animeName: String,
            russianName: String?,
        ): ParserResult =
            withContext(appDispatchers.io) {
                Logger.withTag("AllohaParser").d { "Getting sources for $animeName / $russianName" }
                val token = playerSettingsManager?.customKodikToken?.value?.ifBlank { null } ?: "free"
                val sources = mutableListOf<VideoSource>()

                try {
                    val searchName = russianName?.takeIf(String::isNotBlank) ?: animeName
                    val response = allohaApi.search(token = token, name = searchName)
                    processResponse(response, sources)
                } catch (e: Throwable) {
                    Logger.withTag("AllohaParser").e(e) { "Failed to fetch Alloha sources" }
                }

                ParserResult(sources = sources, hasPartialFailures = false)
            }

        private fun processResponse(
            response: AllohaResponseDto,
            out: MutableList<VideoSource>
        ) {
            val data = response.data ?: return
            val seasons = data.seasons ?: return

            for ((_, seasonDto) in seasons) {
                val episodesMap = seasonDto.episodes ?: continue
                val episodeList = mutableListOf<EpisodeSource>()

                for ((epKey, epDto) in episodesMap) {
                    val epNum = epKey.toIntOrNull() ?: 1
                    val url = epDto.iframe.orEmpty()
                    if (url.isNotBlank()) {
                        episodeList.add(EpisodeSource(number = epNum, url = url))
                    }
                }

                if (episodeList.isNotEmpty()) {
                    out.add(
                        VideoSource(
                            name = "Alloha",
                            translationName = data.name.orEmpty(),
                            translationType = TranslationType.VO,
                            providerId = BuiltInProviders.ALLOHA,
                            episodes = episodeList.sortedBy { it.number },
                        ),
                    )
                }
            }
        }
    }
