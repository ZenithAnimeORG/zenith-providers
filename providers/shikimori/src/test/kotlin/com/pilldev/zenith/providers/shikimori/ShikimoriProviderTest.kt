package com.pilldev.zenith.providers.shikimori

import com.pilldev.zenith.provider.model.MediaId
import com.pilldev.zenith.provider.model.MediaRef
import com.pilldev.zenith.provider.model.PluginManifest
import com.pilldev.zenith.provider.model.ProviderCapability
import com.pilldev.zenith.provider.model.ProviderId
import com.pilldev.zenith.provider.model.ProviderResult
import com.pilldev.zenith.provider.model.UserMediaStatus
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShikimoriProviderTest {

    private val manifest = PluginManifest(
        id = ProviderId("shikimori"),
        name = "Shikimori",
        version = "1.0.0",
        capabilities = setOf(ProviderCapability.CATALOG, ProviderCapability.TRACKER, ProviderCapability.POSTER_SOURCE),
    )

    @Test
    fun testSearchReturnsItems() = runTest {
        val mockEngine = MockEngine { request ->
            val body = """
            {
              "data": {
                "animes": [
                  {
                    "id": "54856",
                    "malId": "54856",
                    "name": "Horimiya: Piece",
                    "russian": "Хоримия: Кусочек",
                    "score": 8.2,
                    "episodes": 13,
                    "airedOn": { "year": 2023 },
                    "poster": { "originalUrl": "/system/animes/original/54856.jpg" }
                  }
                ]
              }
            }
            """.trimIndent()
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }

        val provider = ShikimoriProvider(manifest, HttpClient(mockEngine))
        val result = provider.search("Horimiya", 1, 20)

        assertTrue(result is ProviderResult.Success)
        val items = (result as ProviderResult.Success).data.items
        assertEquals(1, items.size)
        assertEquals("Хоримия: Кусочек", items[0].title)
        assertEquals("54856", items[0].mediaRef.externalIds[ProviderId("shikimori")])
        assertEquals("54856", items[0].mediaRef.externalIds[ProviderId("mal")])
    }

    @Test
    fun testGetDetailsReturnsParsedDetails() = runTest {
        val mockEngine = MockEngine { request ->
            val body = """
            {
              "data": {
                "animes": [
                  {
                    "id": "54856",
                    "malId": "54856",
                    "name": "Horimiya: Piece",
                    "russian": "Хоримия: Кусочек",
                    "kind": "tv",
                    "status": "released",
                    "score": 8.2,
                    "episodes": 13,
                    "episodesAired": 13,
                    "rating": "pg_13",
                    "description": "Продолжение истории любви Кёко и Идзуми.",
                    "poster": { "originalUrl": "https://shikimori.one/system/animes/original/54856.jpg" },
                    "genres": [{ "name": "Comedy", "russian": "Комедия" }],
                    "studios": [{ "name": "CloverWorks" }]
                  }
                ]
              }
            }
            """.trimIndent()
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }

        val provider = ShikimoriProvider(manifest, HttpClient(mockEngine))
        val result = provider.getDetails(MediaRef(MediaId("shikimori:54856"), mapOf(ProviderId("shikimori") to "54856")))

        assertTrue(result is ProviderResult.Success)
        val details = (result as ProviderResult.Success).data
        assertEquals("Хоримия: Кусочек", details.title)
        assertEquals("Horimiya: Piece", details.originalTitle)
        assertEquals(13, details.episodesCount)
        assertEquals(listOf("Комедия"), details.genres)
        assertEquals(listOf("CloverWorks"), details.studios)
    }

    @Test
    fun testTrackerNoOpWhenUnauthenticated() = runTest {
        val mockEngine = MockEngine { respond("{}", HttpStatusCode.OK) }
        val provider = ShikimoriProvider(manifest, HttpClient(mockEngine))

        val watchedResult = provider.onEpisodeWatched(MediaRef(MediaId("shikimori:54856")), 5)
        assertTrue(watchedResult is ProviderResult.Success)

        val statusResult = provider.syncStatus(MediaRef(MediaId("shikimori:54856")), UserMediaStatus.WATCHING)
        assertTrue(statusResult is ProviderResult.Success)
    }
}
