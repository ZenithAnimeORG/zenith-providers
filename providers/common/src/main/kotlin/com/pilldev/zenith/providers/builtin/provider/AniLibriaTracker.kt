package com.pilldev.zenith.providers.builtin.provider

import com.pilldev.zenith.domain.model.StandardMirrors
import com.pilldev.zenith.domain.model.TorrentTitleParser
import com.pilldev.zenith.domain.model.TranslationType
import com.pilldev.zenith.domain.model.toProviderMirrorSpec
import com.pilldev.zenith.provider.BaseZenithProvider
import com.pilldev.zenith.provider.context.ProviderContext
import com.pilldev.zenith.provider.model.ProviderCapability
import com.pilldev.zenith.provider.model.ProviderId
import com.pilldev.zenith.provider.model.ProviderMetadata
import com.pilldev.zenith.provider.model.ProviderTestResult
import com.pilldev.zenith.provider.model.measureTest
import com.pilldev.zenith.providers.builtin.api.AniLibertyApi
import com.pilldev.zenith.providers.builtin.api.ktorfit.AniLibertyReleaseDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

class AniLibriaTracker
    constructor(
        private val api: AniLibertyApi,
        private val json: Json,
        private val appDispatchers: com.pilldev.zenith.domain.repository.AppDispatchers,
        override val name: String = "AniLibria",
    ) : BaseZenithProvider(),
        TorrentTracker {
        override val metadata: ProviderMetadata =
            ProviderMetadata(
                id = ProviderId("anilibria_tracker"),
                name = "AniLibria",
                version = "1.0.0",
                capabilities = setOf(ProviderCapability.TORRENT_SOURCE),
                description = "Официальные торрент-раздачи команды AniLibria",
                pros = listOf("Прямые раздачи релизов AniLibria", "Высокая скорость сидов"),
                cons = listOf("Только релизы AniLibria"),
                mirrors = StandardMirrors.ANI_LIBRIA.map { it.toProviderMirrorSpec() },
            )

        override suspend fun test(context: ProviderContext): ProviderTestResult =
            measureTest {
                val response = api.appSearch("Bocchi the Rock!")
                val releases = parseReleases(response)
                "AniLibria трекер активен (найдено релизов: ${releases.size})"
            }

        override suspend fun search(
            query: String,
            russianName: String?,
        ): List<TorrentResult> =
            kotlinx.coroutines.withContext(appDispatchers.io) {
                val results = mutableListOf<TorrentResult>()
                try {
                    val searchQueries = mutableListOf(query)
                    russianName?.let { searchQueries.add(it) }
                    val cleanQuery = com.pilldev.zenith.provider.matcher.AnimeTitleMatcher
                        .cleanForSearch(query)
                    if (cleanQuery != query && cleanQuery.isNotBlank()) searchQueries.add(cleanQuery)

                    var releases = emptyList<AniLibertyReleaseDto>()
                    for (q in searchQueries.distinct()) {
                        val response = api.appSearch(q)
                        val found = parseReleases(response)
                        if (found.isNotEmpty()) {
                            releases = found
                            break
                        }
                    }

                    for (release in releases) {
                        // If the initial release doesn't have torrents, fetch full release
                        var currentRelease = release
                        if (currentRelease.torrents.isNullOrEmpty()) {
                            try {
                                val fullResponse = api.getRelease(release.id.toString(), include = "episodes,torrents")
                                val fullRelease = parseSingleRelease(fullResponse)
                                if (fullRelease != null) {
                                    currentRelease = fullRelease
                                }
                            } catch (_: Exception) {
                                continue
                            }
                        }

                        currentRelease.torrents?.forEach { torrent ->
                            val rawTitle = "${currentRelease.name?.main ?: "Unknown"} - ${torrent.series?.description ?: torrent.series?.value ?: "Full"}"
                            val parsed = TorrentTitleParser.parse(rawTitle)
                            val title = parsed.cleanTitle
                            val tags = parsed.tags

                            results.add(
                                TorrentResult(
                                    title = title,
                                    infoHash = torrent.hash ?: "",
                                    magnetUri = torrent.magnet ?: constructMagnet(torrent.hash ?: "", currentRelease.name?.main ?: ""),
                                    size = torrent.size ?: 0L,
                                    seeders = torrent.seeders ?: 0,
                                    leechers = torrent.leechers ?: 0,
                                    quality = torrent.quality?.description ?: torrent.quality?.value ?: "1080p",
                                    trackerName = name,
                                    translationName = "AniLibria",
                                    translationType = TranslationType.VO,
                                    tags = tags,
                                ),
                            )
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    co.touchlab.kermit.Logger
                        .withTag("AniLibriaTracker")
                        .e(e) { "Error searching" }
                }
                results
            }

        private fun parseReleases(element: JsonElement): List<AniLibertyReleaseDto> =
            try {
                val content =
                    if (element is JsonObject) {
                        element["data"] ?: element
                    } else {
                        element
                    }
                if (content is JsonArray) {
                    content.mapNotNull {
                        try {
                            json.decodeFromJsonElement<AniLibertyReleaseDto>(
                                it,
                            )
                        } catch (_: Exception) {
                            null
                        }
                    }
                } else {
                    listOfNotNull(
                        try {
                            json.decodeFromJsonElement<AniLibertyReleaseDto>(content)
                        } catch (_: Exception) {
                            null
                        },
                    )
                }
            } catch (_: Exception) {
                emptyList()
            }

        private fun parseSingleRelease(element: JsonElement): AniLibertyReleaseDto? =
            try {
                val content =
                    if (element is JsonObject) {
                        element["data"] ?: element
                    } else {
                        element
                    }
                json.decodeFromJsonElement<AniLibertyReleaseDto>(content)
            } catch (_: Exception) {
                null
            }

        private fun constructMagnet(
            hash: String,
            name: String,
        ): String {
            val trackers =
                listOf(
                    "http://anilibria.top:6969/announce",
                    "http://re-tracker.ru/announce",
                    "http://tracker.anilibria.tv:6969/announce",
                    "udp://tracker.opentrackr.org:1337/announce",
                )
            return TorrentTitleParser.buildMagnetUri(hash, name, trackers)
        }
    }
