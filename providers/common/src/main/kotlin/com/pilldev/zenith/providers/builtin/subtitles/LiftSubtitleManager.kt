package com.pilldev.zenith.providers.builtin.subtitles

import co.touchlab.kermit.Logger
import com.pilldev.zenith.domain.model.AnimeTitleMatcher
import com.pilldev.zenith.domain.model.ExternalSubtitle
import com.pilldev.zenith.domain.repository.AppDispatchers
import com.pilldev.zenith.domain.repository.SubtitleRepository
import com.pilldev.zenith.providers.builtin.api.LiftApi
import com.pilldev.zenith.providers.builtin.api.ktorfit.LiftPlaylistResponseDto
import com.pilldev.zenith.providers.builtin.api.ktorfit.LiftSubtitleDto
import com.pilldev.zenith.providers.builtin.parser.LiftParser
import io.ktor.util.collections.ConcurrentMap
import kotlinx.coroutines.withContext
import kotlin.math.abs

open class LiftSubtitleManager(
    private val liftParser: LiftParser,
    private val liftApi: LiftApi,
    private val appDispatchers: AppDispatchers,
) : SubtitleRepository {
    private val logger = Logger.withTag("LiftSubtitleManager")
    private val directUrlCache = ConcurrentMap<Long, String>()

    override suspend fun searchSubtitles(
        query: String,
        languages: String,
        episodeNumber: Int?,
        seasonNumber: Int?,
    ): List<ExternalSubtitle> =
        withContext(appDispatchers.io) {
            try {
                val playlist = findOrFetchPlaylist(query) ?: return@withContext emptyList()
                val targetEpisodeNum = episodeNumber ?: 1
                val targetSeasonNum = seasonNumber ?: AnimeTitleMatcher.extractSeasonNumber(query)
                val subDtos = extractSubtitlesForEpisode(playlist, targetEpisodeNum, targetSeasonNum)

                subDtos.mapIndexed { idx, sub ->
                    val rawHash = abs(sub.url.hashCode().toLong())
                    val fileId = if (rawHash != 0L) rawHash else (idx + 1000L)
                    directUrlCache[fileId] = sub.url

                    val subName = sub.name?.ifBlank { null } ?: "Субтитры"
                    ExternalSubtitle(
                        fileId = fileId,
                        language = detectLanguage(subName),
                        releaseName = "Lift: $subName",
                        directUrl = sub.url,
                        provider = "Lift",
                    )
                }
            } catch (e: Exception) {
                logger.w(e) { "Failed searching Lift subtitles for query '$query'" }
                emptyList()
            }
        }

    override suspend fun getDownloadLink(fileId: Long): String? =
        withContext(appDispatchers.io) {
            directUrlCache[fileId]
        }

    private suspend fun findOrFetchPlaylist(query: String): LiftPlaylistResponseDto? {
        val cached =
            liftParser.playlistCache.values.firstOrNull { playlist ->
                playlist.title?.let { AnimeTitleMatcher.score(query, it) > 50_000 } == true
            }
        if (cached != null) return cached

        val searchResp = runCatching { liftApi.search(query) }.getOrNull() ?: return null
        val bestCandidate =
            searchResp.items.maxByOrNull { candidate ->
                val titles = listOfNotNull(candidate.name, candidate.originName)
                AnimeTitleMatcher.scoreWithSynonyms(listOf(query), titles)
            } ?: searchResp.items.firstOrNull() ?: return null

        return runCatching { liftApi.getPlaylist(bestCandidate.id.toString()) }.getOrNull()
    }

    private fun extractSubtitlesForEpisode(
        playlist: LiftPlaylistResponseDto,
        episodeNumber: Int,
        seasonNumber: Int?,
    ): List<LiftSubtitleDto> {
        val seasons = playlist.seasons
        if (!seasons.isNullOrEmpty()) {
            val targetSeason =
                if (seasonNumber != null) {
                    seasons.firstOrNull { it.season == seasonNumber && it.episodes.isNotEmpty() }
                        ?: seasons.firstOrNull { it.season == seasonNumber }
                        ?: seasons.getOrNull(seasonNumber - 1)
                        ?: seasons.first()
                } else {
                    seasons.first()
                }

            val ep =
                targetSeason.episodes.firstOrNull {
                    it.episode.toIntOrNull() == episodeNumber
                } ?: targetSeason.episodes.getOrNull(episodeNumber - 1)

            if (ep != null && ep.subtitles.isNotEmpty()) {
                return ep.subtitles
            }
        }

        return playlist.subtitles
    }

    private fun detectLanguage(name: String): String {
        val lower = name.lowercase()
        return when {
            lower.contains("рус") || lower.contains("rus") || lower.contains("russian") -> "ru"
            lower.contains("eng") || lower.contains("анг") || lower.contains("english") -> "en"
            lower.contains("jap") || lower.contains("яп") || lower.contains("japanese") -> "ja"
            else -> "ru"
        }
    }
}
