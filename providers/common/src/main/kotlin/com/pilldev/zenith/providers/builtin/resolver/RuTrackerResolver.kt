package com.pilldev.zenith.providers.builtin.resolver

import com.fleeksoft.ksoup.Ksoup
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText

open class RuTrackerResolver
    constructor(
        private val client: HttpClient,
        private val settings: com.pilldev.zenith.domain.repository.PlayerSettingsRepository,
    ) {
        open suspend fun resolveMagnet(topicId: String): String? {
            val baseUrl = settings.ruTrackerBaseUrl.value.ifBlank { "https://rutracker.org" }
            val sessionId = settings.ruTrackerSessionId.value
            val data = settings.ruTrackerData.value

            if (sessionId.isBlank()) return null

            val url = "$baseUrl/forum/viewtopic.php?t=$topicId"

            return try {
                val response =
                    client.get(url) {
                        header("Cookie", "bb_session=$sessionId; bb_data=$data")
                        header("User-Agent", settings.userAgent.value)
                    }

                val html = response.bodyAsText()
                val doc = Ksoup.parse(html)

                // Search for magnet link in the page
                val magnetLink = doc.select("a.magnet-link").firstOrNull()?.attr("href")
                if (magnetLink != null) return magnetLink

                // If magnet not found (e.g., topic locked or hidden behind another element)
                // try to find it via standard magnet: pattern in text or other links
                val allLinks = doc.select("a[href^='magnet:']")
                allLinks.firstOrNull()?.attr("href")
            } catch (e: Exception) {
                co.touchlab.kermit.Logger
                    .withTag("RuTrackerResolver")
                    .e(e) { "Error resolving magnet for topic $topicId" }
                null
            }
        }
    }
