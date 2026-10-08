package com.pilldev.zenith.providers.builtin.api

import co.touchlab.kermit.Logger
import com.pilldev.zenith.domain.repository.PlayerSettingsRepository
import com.pilldev.zenith.providers.builtin.api.ktorfit.LiftKtorfitApi
import com.pilldev.zenith.providers.builtin.api.ktorfit.LiftPlaylistResponseDto
import com.pilldev.zenith.providers.builtin.api.ktorfit.LiftSearchResponseDto
import com.pilldev.zenith.providers.builtin.api.ktorfit.createLiftKtorfitApi
import com.pilldev.zenith.providers.builtin.net.BuiltInEndpoints
import com.pilldev.zenith.providers.builtin.parser.LiftMirrorSelector
import de.jensklingenberg.ktorfit.Ktorfit
import io.ktor.client.HttpClient
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header

open class LiftApi
    constructor(
        open val ktorfitApi: LiftKtorfitApi,
        private val playerSettingsManager: PlayerSettingsRepository,
        private val client: HttpClient = HttpClient(),
    ) {
        private val logger = Logger.withTag("LiftApi")

        private val clientWithAuth: HttpClient by lazy {
            client.config {
                defaultRequest {
                    header("Authorization", LiftMirrorSelector.BASIC_AUTH_HEADER)
                }
            }
        }

        constructor(
            client: HttpClient,
            playerSettingsManager: PlayerSettingsRepository,
        ) : this(
            Ktorfit
                .Builder()
                .httpClient(
                    client.config {
                        defaultRequest {
                            header("Authorization", LiftMirrorSelector.BASIC_AUTH_HEADER)
                        }
                    },
                ).baseUrl(BuiltInEndpoints.LIFT)
                .build()
                .createLiftKtorfitApi(),
            playerSettingsManager,
            client,
        )

        private fun getApiForMirror(mirrorUrl: String): LiftKtorfitApi {
            val normalized = LiftMirrorSelector.normalizeMirrorUrl(mirrorUrl)
            if (normalized == LiftMirrorSelector.normalizeMirrorUrl(BuiltInEndpoints.LIFT)) {
                return ktorfitApi
            }
            return Ktorfit
                .Builder()
                .httpClient(clientWithAuth)
                .baseUrl(normalized)
                .build()
                .createLiftKtorfitApi()
        }

        private fun getCandidateMirrors(): List<String> {
            val custom = playerSettingsManager.liftBaseUrl.value
            val ranked = playerSettingsManager.liftRankedMirrors.value
            val candidates = mutableListOf<String>()
            if (custom.isNotBlank()) candidates.add(LiftMirrorSelector.normalizeMirrorUrl(custom))
            ranked.filter { it.isNotBlank() }.forEach { candidates.add(LiftMirrorSelector.normalizeMirrorUrl(it)) }
            LiftMirrorSelector.candidateMirrors.forEach { candidates.add(it) }
            return candidates.distinct()
        }

        open suspend fun search(query: String): LiftSearchResponseDto {
            val mirrors = getCandidateMirrors()
            var lastError: Exception? = null

            for (mirror in mirrors) {
                try {
                    val api = getApiForMirror(mirror)
                    return api.search(query)
                } catch (e: Exception) {
                    logger.w(e) { "Lift search failed on mirror $mirror for query '$query'" }
                    lastError = e
                }
            }
            throw lastError ?: Exception("All Lift mirrors failed for search '$query'")
        }

        open suspend fun getPlaylist(id: String): LiftPlaylistResponseDto {
            val mirrors = getCandidateMirrors()
            var lastError: Exception? = null

            for (mirror in mirrors) {
                try {
                    val api = getApiForMirror(mirror)
                    return api.getPlaylist(id)
                } catch (e: Exception) {
                    logger.w(e) { "Lift getPlaylist failed on mirror $mirror for id '$id'" }
                    lastError = e
                }
            }
            throw lastError ?: Exception("All Lift mirrors failed for playlist id '$id'")
        }
    }
