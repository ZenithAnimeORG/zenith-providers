package com.pilldev.zenith.providers.builtin.provider
import com.fleeksoft.ksoup.Ksoup
import com.pilldev.zenith.domain.model.TorrentTitleParser
import com.pilldev.zenith.domain.model.TranslationType
import com.pilldev.zenith.domain.model.toProviderMirrorSpec
import com.pilldev.zenith.provider.BaseZenithProvider
import com.pilldev.zenith.provider.context.ProviderContext
import com.pilldev.zenith.provider.matcher.AnimeTitleMatcher
import com.pilldev.zenith.provider.model.ProviderCapability
import com.pilldev.zenith.provider.model.ProviderId
import com.pilldev.zenith.provider.model.ProviderMetadata
import com.pilldev.zenith.provider.model.ProviderSettingSpec
import com.pilldev.zenith.provider.model.ProviderTestResult
import com.pilldev.zenith.provider.model.measureTest
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter

class RuTorTracker
    constructor(
        private val settings: com.pilldev.zenith.domain.repository.PlayerSettingsRepository,
        private val client: HttpClient,
        override val name: String = "RuTor",
    ) : BaseZenithProvider(),
        TorrentTracker {
        override val metadata: ProviderMetadata =
            ProviderMetadata(
                id = ProviderId("rutor"),
                name = "RuTor",
                version = "1.0.0",
                capabilities = setOf(ProviderCapability.TORRENT_SOURCE),
                description = "Свободный торрент-трекер без регистрации",
                pros = listOf("Множество релизов в высоком качестве", "Не требует авторизации"),
                cons = listOf("Периодические блокировки доменов"),
                settingsSchema =
                    listOf(
                        ProviderSettingSpec(
                            key = "mirror",
                            label = "Зеркало RuTor",
                            defaultValue = "https://free-rutor.org",
                            description = "URL или домен рабочего зеркала RuTor",
                        ),
                    ),
                mirrors = com.pilldev.zenith.domain.model.StandardMirrors.RUTOR
                    .map { it.toProviderMirrorSpec() },
            )

        override suspend fun test(context: ProviderContext): ProviderTestResult =
            measureTest {
                val mirror = effectiveMirror().ifBlank { context.getSetting("mirror") ?: settings.ruTorBaseUrl.value }
                val baseUrl = mirror.removeSuffix("/")
                val response: HttpResponse = client.get("$baseUrl/index.php")
                if (response.status.value in 200..399) {
                    "RuTor доступен ($baseUrl)"
                } else {
                    error("RuTor вернул статус ${response.status}")
                }
            }

        override suspend fun search(
            query: String,
            russianName: String?,
        ): List<TorrentResult> {
            val results = mutableListOf<TorrentResult>()
            try {
                val mirror = effectiveMirror().ifBlank { getSetting("mirror") ?: settings.ruTorBaseUrl.value }
                val baseUrl = mirror.removeSuffix("/")
                val queries = mutableListOf<String>()
                russianName?.let {
                    val cleanRu = com.pilldev.zenith.provider.matcher.AnimeTitleMatcher
                        .cleanForSearch(it)
                    if (cleanRu.isNotBlank()) queries.add(cleanRu)
                    val baseRu = it.substringBefore(":").substringBefore("-").trim()
                    if (baseRu != it && baseRu.length >= 3) queries.add(baseRu)
                }
                val cleanEn = com.pilldev.zenith.provider.matcher.AnimeTitleMatcher
                    .cleanForSearch(query)
                if (cleanEn.isNotBlank()) queries.add(cleanEn)
                val baseEn = query.substringBefore(":").substringBefore("-").trim()
                if (baseEn != query && baseEn.length >= 3) queries.add(baseEn)

                for (cleanQuery in queries.distinct()) {
                    val url = "$baseUrl/search/0/0/0/0/${cleanQuery.encodeURLParameter(spaceToPlus = true)}"
                    val response: HttpResponse = client.get(url)
                    val html = response.bodyAsText()
                    val doc = Ksoup.parse(html)

                    val rows = doc.select("tr.gai, tr.tum, tr.g, tr.o, #index tr:not(.backgr)")
                    if (rows.isEmpty()) continue
                    for (row in rows) {
                        val td = row.select("td")
                        if (td.size < 4) continue

                        val titleEl = td[1].select("a").lastOrNull { !it.attr("href").startsWith("magnet:") } ?: continue
                        val rawTitle = titleEl.text()

                        val magnetEl = td[1].select("a[href^=magnet:]").firstOrNull()
                        val magnet = magnetEl?.attr("href")

                        val sizeStr = td[2].text()
                        val size = TorrentTitleParser.parseByteSize(sizeStr)

                        val seeders = td[3]
                            .select("span.green")
                            .text()
                            .replace("\u00A0", "")
                            .trim()
                            .toIntOrNull() ?: 0
                        val leechers = td[3]
                            .select("span.red")
                            .text()
                            .replace("\u00A0", "")
                            .trim()
                            .toIntOrNull() ?: 0

                        val parsed = TorrentTitleParser.parse(rawTitle)

                        results.add(
                            TorrentResult(
                                title = parsed.cleanTitle,
                                magnetUri = magnet,
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
                    if (results.isNotEmpty()) break
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                co.touchlab.kermit.Logger
                    .withTag("RuTorTracker")
                    .e(e) { "Search error" }
            }
            return results
        }
    }
