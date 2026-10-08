package com.pilldev.zenith.providers.builtin.parser

import co.touchlab.kermit.Logger
import com.pilldev.zenith.domain.model.AnimeTitleMatcher
import com.pilldev.zenith.domain.model.EpisodeSource
import com.pilldev.zenith.domain.model.TranslationType
import com.pilldev.zenith.domain.model.VideoSource
import com.pilldev.zenith.provider.model.BuiltInProviders
import com.pilldev.zenith.providers.builtin.api.LiftApi
import com.pilldev.zenith.providers.builtin.api.ktorfit.LiftPlaylistResponseDto
import com.pilldev.zenith.providers.builtin.api.ktorfit.LiftSearchItemDto
import io.ktor.util.collections.ConcurrentMap

open class LiftParser
    constructor(
        private val liftApi: LiftApi,
    ) : AnimeParser {
        override val name: String = "Lift"

        private val logger = Logger.withTag("LiftParser")

        // In-memory cache of playlist by Shikimori ID for instant subtitle and episode access
        public val playlistCache: ConcurrentMap<Int, LiftPlaylistResponseDto> = ConcurrentMap()

        override suspend fun getSources(
            shikimoriId: Int,
            animeName: String,
            russianName: String?,
        ): ParserResult {
            logger.d { "LiftParser: Searching for $animeName ($russianName) [Shikimori ID: $shikimoriId]" }
            try {
                val queries = buildSearchQueries(animeName, russianName)
                val searchCandidates = mutableListOf<LiftSearchItemDto>()

                for (query in queries) {
                    try {
                        val resp = liftApi.search(query)
                        if (resp.items.isNotEmpty()) {
                            searchCandidates.addAll(resp.items)
                            break
                        }
                    } catch (e: Exception) {
                        logger.w(e) { "Lift search query failed: $query" }
                    }
                }

                if (searchCandidates.isEmpty()) {
                    logger.d { "Lift: No search candidates found for $animeName" }
                    return ParserResult(emptyList())
                }

                val queryTitles = listOfNotNull(animeName, russianName).filter { it.isNotBlank() }
                val bestCandidate =
                    searchCandidates.maxByOrNull { candidate ->
                        val candidateTitles = listOfNotNull(candidate.name, candidate.originName).filter { it.isNotBlank() }
                        AnimeTitleMatcher.scoreWithSynonyms(queryTitles, candidateTitles)
                    } ?: searchCandidates.first()

                logger.d { "Lift: Selected candidate ID ${bestCandidate.id} ('${bestCandidate.name}' / '${bestCandidate.originName}')" }

                val playlist = liftApi.getPlaylist(bestCandidate.id.toString())
                playlistCache[shikimoriId] = playlist

                val targetSeason =
                    listOfNotNull(russianName, animeName)
                        .firstNotNullOfOrNull { AnimeTitleMatcher.extractSeasonNumber(it) } ?: 1

                val sources = parsePlaylistToSources(playlist, targetSeason)
                return ParserResult(sources)
            } catch (e: Exception) {
                logger.e(e) { "LiftParser: Error getting sources for $animeName" }
                return ParserResult(emptyList(), hasPartialFailures = true)
            }
        }

        private fun buildSearchQueries(
            animeName: String,
            russianName: String?,
        ): List<String> {
            val queries = mutableListOf<String>()
            russianName?.takeIf { it.isNotBlank() }?.let { queries.add(it.trim()) }
            if (animeName.isNotBlank()) queries.add(animeName.trim())

            russianName?.let {
                val baseRu = it.substringBefore(":").substringBefore("-").trim()
                if (baseRu.length > 3 && baseRu != it) queries.add(baseRu)
            }
            val baseEn = animeName.substringBefore(":").substringBefore("-").trim()
            if (baseEn.length > 3 && baseEn != animeName) queries.add(baseEn)

            return queries.distinct()
        }

        private fun parsePlaylistToSources(
            playlist: LiftPlaylistResponseDto,
            targetSeason: Int = 1,
        ): List<VideoSource> {
            val seasons = playlist.seasons
            if (!seasons.isNullOrEmpty()) {
                val selectedSeason =
                    seasons.firstOrNull { it.season == targetSeason && it.episodes.isNotEmpty() }
                        ?: seasons.firstOrNull { it.season == targetSeason }
                        ?: seasons.getOrNull(targetSeason - 1)?.takeIf { it.episodes.isNotEmpty() }
                        ?: seasons.firstOrNull { it.episodes.isNotEmpty() }
                        ?: seasons.first()
                return parseEpisodicPlaylist(selectedSeason)
            }

            // Single movie or video stream at playlist root
            val rootHls = playlist.streams?.hls
            if (!rootHls.isNullOrBlank()) {
                return parseMoviePlaylist(playlist, rootHls)
            }

            return emptyList()
        }

        private fun parseEpisodicPlaylist(season: com.pilldev.zenith.providers.builtin.api.ktorfit.LiftSeasonDto): List<VideoSource> {
            val episodes = season.episodes
            if (episodes.isEmpty()) return emptyList()

            // Find all unique audio track names across episodes
            val trackNames =
                episodes
                    .flatMap { it.audio?.names ?: emptyList() }
                    .filter { it.isNotBlank() }
                    .distinct()

            val effectiveTracks = if (trackNames.isEmpty()) listOf("Lift") else trackNames

            return effectiveTracks
                .map { trackName ->
                    val episodeSources = mutableListOf<EpisodeSource>()

                    episodes.forEachIndexed { index, ep ->
                        val epNumber = ep.episode.toIntOrNull() ?: (index + 1)
                        val streamUrl = ep.streams?.hls
                        if (!streamUrl.isNullOrBlank()) {
                            episodeSources.add(
                                EpisodeSource(
                                    number = epNumber,
                                    url = streamUrl,
                                    quality = mapOf("Авто" to streamUrl),
                                ),
                            )
                        }
                    }

                    VideoSource(
                        name = "Lift",
                        translationName = trackName,
                        translationType = guessTranslationType(trackName),
                        providerId = BuiltInProviders.LIFT,
                        episodes = episodeSources.distinctBy { it.number }.sortedBy { it.number },
                        qualities = episodeSources.firstOrNull()?.quality,
                    )
                }.filter { it.episodes.isNotEmpty() }
        }

        private fun parseMoviePlaylist(
            playlist: LiftPlaylistResponseDto,
            streamUrl: String,
        ): List<VideoSource> {
            val trackNames = playlist.audio?.names?.filter { it.isNotBlank() } ?: emptyList()
            val effectiveTracks = if (trackNames.isEmpty()) listOf("Lift") else trackNames

            return effectiveTracks.map { trackName ->
                val episode =
                    EpisodeSource(
                        number = 1,
                        url = streamUrl,
                        quality = mapOf("Авто" to streamUrl),
                    )

                VideoSource(
                    name = "Lift",
                    translationName = trackName,
                    translationType = guessTranslationType(trackName),
                    providerId = BuiltInProviders.LIFT,
                    episodes = listOf(episode),
                    qualities = mapOf("Авто" to streamUrl),
                )
            }
        }

        private fun guessTranslationType(name: String): TranslationType {
            val lower = name.lowercase()
            return when {
                lower.contains("дубляж") || lower.contains("dub") -> TranslationType.DUB
                lower.contains("субтитр") || lower.contains("sub") -> TranslationType.SUB
                else -> TranslationType.VO
            }
        }
    }
