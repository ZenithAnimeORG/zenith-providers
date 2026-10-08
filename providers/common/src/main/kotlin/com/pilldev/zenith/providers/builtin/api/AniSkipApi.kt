package com.pilldev.zenith.providers.builtin.api

import com.pilldev.zenith.domain.model.AniSkipResponse
import com.pilldev.zenith.domain.model.AniSkipResult
import com.pilldev.zenith.domain.model.Interval
import com.pilldev.zenith.providers.builtin.api.ktorfit.AniSkipKtorfitApi
import com.pilldev.zenith.providers.builtin.api.ktorfit.createAniSkipKtorfitApi
import de.jensklingenberg.ktorfit.Ktorfit
import io.ktor.client.HttpClient

open class AniSkipApi
    constructor(
        private val ktorfitApi: AniSkipKtorfitApi,
    ) {
        constructor(client: HttpClient) : this(
            Ktorfit
                .Builder()
                .httpClient(client)
                .baseUrl(com.pilldev.zenith.providers.builtin.net.BuiltInEndpoints.ANI_SKIP)
                .build()
                .createAniSkipKtorfitApi()
        )

        constructor() : this(HttpClient())

        open suspend fun getSkipTimes(
            malId: Int,
            episodeNumber: Int,
            episodeLength: Double? = null,
        ): AniSkipResponse {
            co.touchlab.kermit.Logger.withTag("AniSkip").d {
                "Requesting skip times for MAL ID: $malId, Episode: $episodeNumber, Length: $episodeLength"
            }
            return try {
                val length = if (episodeLength != null && episodeLength.isFinite() && episodeLength > 0) episodeLength else 0.0
                val targetUrl = "${com.pilldev.zenith.providers.builtin.net.BuiltInEndpoints.ANI_SKIP}skip-times/$malId/$episodeNumber?types[]=op&types[]=ed&types[]=recap&types[]=mixed-op&types[]=mixed-ed&episodeLength=$length"
                val dto = ktorfitApi.getSkipTimesByUrl(targetUrl)
                val results = dto.results?.map { item ->
                    AniSkipResult(
                        interval = item.interval?.let {
                            Interval(
                                startTime = it.startTime,
                                endTime = it.endTime,
                            )
                        },
                        skipType = item.skipType ?: "",
                        episodeLength = item.episodeLength ?: 0.0,
                        start = item.interval?.startTime,
                        end = item.interval?.endTime,
                    )
                } ?: emptyList()
                AniSkipResponse(found = dto.found, results = results)
            } catch (e: Exception) {
                co.touchlab.kermit.Logger
                    .withTag("AniSkip")
                    .d { "Skip times not available for $malId EP $episodeNumber (${e.message})" }
                AniSkipResponse(found = false, results = emptyList())
            }
        }
    }
