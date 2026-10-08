package com.pilldev.zenith.providers.builtin.provider

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.select.Elements
import com.pilldev.zenith.domain.model.StandardMirrors
import com.pilldev.zenith.domain.model.TorrentTitleParser
import com.pilldev.zenith.domain.model.TranslationType
import com.pilldev.zenith.domain.model.toProviderMirrorSpec
import com.pilldev.zenith.domain.repository.PlayerSettingsRepository
import com.pilldev.zenith.provider.BaseZenithProvider
import com.pilldev.zenith.provider.context.ProviderContext
import com.pilldev.zenith.provider.model.ProviderCapability
import com.pilldev.zenith.provider.model.ProviderId
import com.pilldev.zenith.provider.model.ProviderMetadata
import com.pilldev.zenith.provider.model.ProviderSettingSpec
import com.pilldev.zenith.provider.model.ProviderTestResult
import com.pilldev.zenith.provider.model.measureTest
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLQueryComponent

class RuTrackerTracker
    constructor(
        private val settings: PlayerSettingsRepository,
        private val client: HttpClient,
        override val name: String = "RuTracker",
    ) : BaseZenithProvider(),
        TorrentTracker {
        override val metadata: ProviderMetadata =
            ProviderMetadata(
                id = ProviderId("rutracker"),
                name = "RuTracker",
                version = "1.0.0",
                capabilities = setOf(ProviderCapability.TORRENT_SOURCE),
                description = "Крупнейший русскоязычный торрент-трекер",
                pros = listOf("Огромный каталог раритетов и полных коллекций"),
                cons = listOf("Требуется авторизация для скачивания торрент-файлов"),
                settingsSchema =
                    listOf(
                        ProviderSettingSpec(
                            key = "mirror",
                            label = "Зеркало RuTracker",
                            defaultValue = "https://rutracker.org",
                            description = "URL или домен рабочего зеркала RuTracker",
                        ),
                    ),
                mirrors = StandardMirrors.RUTRACKER.map { it.toProviderMirrorSpec() },
            )

        override suspend fun test(context: ProviderContext): ProviderTestResult =
            measureTest {
                val mirror = effectiveMirror().ifBlank { context.getSetting("mirror") ?: "https://rutracker.org" }
                val baseUrl = mirror.removeSuffix("/")
                val response: HttpResponse =
                    client.get("$baseUrl/forum/index.php") {
                        header("User-Agent", com.pilldev.zenith.provider.net.BrowserHeaders.CHROME_DESKTOP_UA)
                    }
                if (response.status.value in 200..399) {
                    "RuTracker доступен ($baseUrl)"
                } else {
                    error("RuTracker вернул статус ${response.status}")
                }
            }

        override suspend fun search(
            query: String,
            russianName: String?,
        ): List<TorrentResult> {
            val results = mutableListOf<TorrentResult>()
            try {
                val mirror = effectiveMirror().ifBlank { getSetting("mirror") ?: "https://rutracker.org" }
                val baseUrl = mirror.removeSuffix("/")
                val encodedQuery = query.encodeURLQueryComponent()
                val url = "$baseUrl/forum/tracker.php?nm=$encodedQuery"

                val response: HttpResponse =
                    client.get(url) {
                        header("User-Agent", com.pilldev.zenith.provider.net.BrowserHeaders.CHROME_DESKTOP_UA)
                    }

                val html = response.bodyAsText()

                if (html.contains("profile.php?mode=register")) {
                    throw AuthExpiredException(name)
                }

                val doc = Ksoup.parse(html)
                val rows: Elements = doc.select("tr.tCenter")

                for (row in rows) {
                    val td: Elements = row.select("td")
                    if (td.size < 10) continue

                    val titleEl: Element = td[3].select("a.genmed").firstOrNull() ?: continue
                    val title = titleEl.text()
                    val topicId = titleEl.attr("href").substringAfter("t=")

                    val seeders = td[6].select("b.seedmed").text().toIntOrNull() ?: 0
                    val leechers = td[7].text().toIntOrNull() ?: 0
                    val sizeStr = td[5].text().substringBefore("↓")
                    val size = TorrentTitleParser.parseByteSize(sizeStr)

                    val parsed = TorrentTitleParser.parse(title)

                    results.add(
                        TorrentResult(
                            title = parsed.cleanTitle,
                            infoHash = null,
                            magnetUri = topicId,
                            size = size,
                            seeders = seeders,
                            leechers = leechers,
                            quality = parsed.normalizedQuality,
                            trackerName = name,
                            translationName = "Multi",
                            translationType = TranslationType.VO,
                            tags = parsed.tags,
                        ),
                    )
                }
            } catch (e: AuthExpiredException) {
                throw e
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                co.touchlab.kermit.Logger
                    .withTag("RuTrackerTracker")
                    .e(e) { "Error searching" }
            }
            return results
        }
    }
