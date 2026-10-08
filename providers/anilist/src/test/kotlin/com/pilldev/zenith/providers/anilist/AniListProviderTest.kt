package com.pilldev.zenith.providers.anilist

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

class AniListProviderTest {

    private val manifest = PluginManifest(
        id = ProviderId("anilist"),
        name = "AniList",
        version = "1.0.0",
        capabilities = setOf(ProviderCapability.CATALOG, ProviderCapability.TRACKER),
    )

    @Test
    fun testSearchReturnsItemsWithIdMal() = runTest {
        val mockEngine = MockEngine { request ->
            val body = """
            {
              "data": {
                "Page": {
                  "pageInfo": { "hasNextPage": false },
                  "media": [
                    {
                      "id": 160089,
                      "idMal": 54856,
                      "title": {
                        "romaji": "Horimiya: Piece",
                        "english": "Horimiya: The Missing Pieces",
                        "native": "ホリミヤ -piece-"
                      },
                      "coverImage": {
                        "extraLarge": "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx160089.jpg"
                      },
                      "averageScore": 82,
                      "seasonYear": 2023,
                      "episodes": 13,
                      "status": "FINISHED"
                    }
                  ]
                }
              }
            }
            """.trimIndent()
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }

        val provider = AniListProvider(manifest, HttpClient(mockEngine))
        val result = provider.search("Horimiya", 1, 20)

        assertTrue(result is ProviderResult.Success)
        val items = (result as ProviderResult.Success).data.items
        assertEquals(1, items.size)
        assertEquals("Horimiya: The Missing Pieces", items[0].title)
        assertEquals("Horimiya: Piece", items[0].originalTitle)
        assertEquals(8.2, items[0].score)
        assertEquals("160089", items[0].mediaRef.externalIds[ProviderId("anilist")])
        assertEquals("54856", items[0].mediaRef.externalIds[ProviderId("mal")])
    }

    @Test
    fun testGetDetailsReturnsParsedDetails() = runTest {
        val mockEngine = MockEngine { request ->
            val body = """
            {
              "data": {
                "Media": {
                  "id": 160089,
                  "idMal": 54856,
                  "title": {
                    "romaji": "Horimiya: Piece",
                    "english": "Horimiya: The Missing Pieces"
                  },
                  "description": "Stories from the manga not animated in the original anime series.",
                  "coverImage": {
                    "extraLarge": "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx160089.jpg"
                  },
                  "bannerImage": "https://s4.anilist.co/file/anilistcdn/media/anime/banner/160089.jpg",
                  "averageScore": 82,
                  "seasonYear": 2023,
                  "episodes": 13,
                  "status": "FINISHED",
                  "genres": ["Comedy", "Romance"],
                  "studios": {
                    "nodes": [{ "name": "CloverWorks" }]
                  }
                }
              }
            }
            """.trimIndent()
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }

        val provider = AniListProvider(manifest, HttpClient(mockEngine))
        val result = provider.getDetails(MediaRef(MediaId("anilist:160089"), mapOf(ProviderId("anilist") to "160089")))

        assertTrue(result is ProviderResult.Success)
        val details = (result as ProviderResult.Success).data
        assertEquals("Horimiya: The Missing Pieces", details.title)
        assertEquals("Horimiya: Piece", details.originalTitle)
        assertEquals(13, details.episodesCount)
        assertEquals(listOf("Comedy", "Romance"), details.genres)
        assertEquals(listOf("CloverWorks"), details.studios)
        assertEquals("54856", details.mediaRef.externalIds[ProviderId("mal")])
    }

    @Test
    fun testTrackerNoOpWhenUnauthenticated() = runTest {
        val mockEngine = MockEngine { respond("{}", HttpStatusCode.OK) }
        val provider = AniListProvider(manifest, HttpClient(mockEngine))

        val watchedResult = provider.onEpisodeWatched(MediaRef(MediaId("anilist:160089")), 3)
        assertTrue(watchedResult is ProviderResult.Success)

        val statusResult = provider.syncStatus(MediaRef(MediaId("anilist:160089")), UserMediaStatus.COMPLETED)
        assertTrue(statusResult is ProviderResult.Success)
    }
}
