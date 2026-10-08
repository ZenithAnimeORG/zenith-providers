package com.pilldev.zenith.providers.builtin.api

import com.pilldev.zenith.domain.repository.PlayerSettingsRepository
import com.pilldev.zenith.providers.builtin.api.ktorfit.AnimeSkipKtorfitApi
import com.pilldev.zenith.providers.builtin.api.ktorfit.createAnimeSkipKtorfitApi
import com.pilldev.zenith.providers.builtin.model.AnimeSkipResponse
import com.pilldev.zenith.providers.builtin.net.BuiltInSecrets
import de.jensklingenberg.ktorfit.Ktorfit
import io.ktor.client.HttpClient

open class AnimeSkipApi
    constructor(
        open val ktorfitApi: AnimeSkipKtorfitApi,
        private val playerSettingsManager: PlayerSettingsRepository? = null,
    ) {
        constructor(
            client: HttpClient,
            playerSettingsManager: PlayerSettingsRepository? = null,
        ) : this(
            Ktorfit
                .Builder()
                .httpClient(client)
                .baseUrl("https://api.anime-skip.com/")
                .build()
                .createAnimeSkipKtorfitApi(),
            playerSettingsManager,
        )

        constructor() : this(HttpClient(), null)

        /**
         * Fetches intro/outro timestamps for the specified AniList ID from AnimeSkip.
         */
        open suspend fun getTimestamps(aniListId: Int): AnimeSkipResponse {
            val query =
                """
                {
                  findShowsByExternalId(service: ANILIST, serviceId: "$aniListId") {
                    id
                    episodes {
                      number
                      timestamps {
                        at
                        type {
                          name
                        }
                      }
                    }
                  }
                }
                """.trimIndent()

            return try {
                val token = playerSettingsManager?.effectiveAnimeSkipClientId?.ifBlank { BuiltInSecrets.ANIME_SKIP_CLIENT_ID }
                    ?: BuiltInSecrets.ANIME_SKIP_CLIENT_ID
                ktorfitApi.getTimestamps(token, mapOf("query" to query))
            } catch (e: Exception) {
                co.touchlab.kermit.Logger
                    .withTag("AnimeSkip")
                    .d { "AnimeSkip timestamps not available for $aniListId (${e.message})" }
                AnimeSkipResponse(data = null)
            }
        }
    }
