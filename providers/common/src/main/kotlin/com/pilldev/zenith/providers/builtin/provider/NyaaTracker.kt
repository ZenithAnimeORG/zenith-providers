package com.pilldev.zenith.providers.builtin.provider
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.parser.Parser
import com.pilldev.zenith.domain.model.StandardMirrors
import com.pilldev.zenith.domain.model.TorrentTitleParser
import com.pilldev.zenith.domain.model.TranslationType
import com.pilldev.zenith.domain.model.toProviderMirrorSpec
import com.pilldev.zenith.provider.BaseZenithProvider
import com.pilldev.zenith.provider.ZenithProvider
import com.pilldev.zenith.provider.ZenithProviderFactory
import com.pilldev.zenith.provider.context.ProviderContext
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

open class NyaaTracker
    constructor(
        private val client: HttpClient = HttpClient(),
        private val appDispatchers: com.pilldev.zenith.domain.repository.AppDispatchers = object : com.pilldev.zenith.domain.repository.AppDispatchers {
            override val main: CoroutineDispatcher = Dispatchers.Main
            override val io: CoroutineDispatcher = Dispatchers.IO
            override val default: CoroutineDispatcher = Dispatchers.Default
            override val unconfined: CoroutineDispatcher = Dispatchers.Unconfined
        },
        override val name: String = "Nyaa.si",
    ) : BaseZenithProvider(),
        TorrentTracker {
        override val metadata: ProviderMetadata =
            ProviderMetadata(
                id = ProviderId("nyaa"),
                name = "Nyaa.si",
                version = "1.0.0",
                capabilities = setOf(ProviderCapability.TORRENT_SOURCE),
                description = "Крупнейший мировой трекер оригинального аниме",
                pros = listOf("Огромная база раздач", "Релизы в высоком качестве"),
                cons = listOf("В основном оригинальная озвучка и ансаб"),
                settingsSchema =
                    listOf(
                        ProviderSettingSpec(
                            key = "mirror",
                            label = "Зеркало Nyaa",
                            defaultValue = "https://nyaa.si",
                            description = "URL или домен рабочего зеркала Nyaa",
                        ),
                    ),
                mirrors = StandardMirrors.NYAA.map { it.toProviderMirrorSpec() },
            )

        override suspend fun test(context: ProviderContext): ProviderTestResult =
            measureTest {
                val mirror = effectiveMirror().ifBlank { context.getSetting("mirror") ?: "https://nyaa.si" }
                val baseUrl = mirror.removeSuffix("/")
                val response: HttpResponse = client.get("$baseUrl/?page=rss")
                if (response.status.value in 200..399) {
                    "Nyaa доступен ($baseUrl)"
                } else {
                    error("Nyaa вернул статус ${response.status}")
                }
            }

        override suspend fun search(
            query: String,
            russianName: String?,
        ): List<TorrentResult> =
            kotlinx.coroutines.withContext(
                appDispatchers.io,
            ) {
                val results = mutableListOf<TorrentResult>()
                try {
                    val mirror = effectiveMirror().ifBlank { getSetting("mirror") ?: "https://nyaa.si" }
                    val baseUrl = mirror.removeSuffix("/")
                    val url = "$baseUrl/?page=rss&q=${query.encodeURLParameter(spaceToPlus = true)}&c=1_2&f=0"
                    val response: HttpResponse = client.get(url)
                    val xml = response.bodyAsText()

                    val doc = Ksoup.parse(xml, Parser.xmlParser())
                    doc.select("item").mapNotNullTo(results) { parseItem(it) }
                    co.touchlab.kermit.Logger
                        .withTag("NyaaTracker")
                        .d { "Found ${results.size} results for $query" }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    co.touchlab.kermit.Logger
                        .withTag("NyaaTracker")
                        .e(e) { "Error searching" }
                }
                results
            }

        private fun parseItem(item: com.fleeksoft.ksoup.nodes.Element): TorrentResult? {
            val rawTitle = item.select("title").text()
            val magnet = item.select("link").text()
            if (rawTitle.isEmpty() || magnet.isEmpty()) return null

            val seeders =
                item
                    .children()
                    .find { it.tagName() == "nyaa:seeders" }
                    ?.text()
                    ?.toIntOrNull() ?: 0
            val leechers =
                item
                    .children()
                    .find { it.tagName() == "nyaa:leechers" }
                    ?.text()
                    ?.toIntOrNull() ?: 0
            val sizeStr = item.children().find { it.tagName() == "nyaa:size" }?.text() ?: ""
            val size = TorrentTitleParser.parseByteSize(sizeStr)

            val parsed = TorrentTitleParser.parse(rawTitle)

            return TorrentResult(
                title = parsed.cleanTitle,
                magnetUri = magnet,
                size = size,
                seeders = seeders,
                leechers = leechers,
                quality = parsed.normalizedQuality,
                trackerName = name,
                translationName = "Subs/Original",
                translationType = TranslationType.SUB,
                tags = parsed.tags,
            )
        }
    }

public class NyaaTrackerFactory : ZenithProviderFactory {
    override fun create(manifest: com.pilldev.zenith.provider.model.PluginManifest): ZenithProvider = NyaaTracker()
}
