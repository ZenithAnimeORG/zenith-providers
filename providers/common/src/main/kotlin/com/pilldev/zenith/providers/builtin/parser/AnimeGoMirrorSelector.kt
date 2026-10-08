package com.pilldev.zenith.providers.builtin.parser

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.util.collections.ConcurrentMap
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.time.TimeSource

public data class AnimeGoMirrorEvaluation(
    val mirrorUrl: String,
    val isOnline: Boolean,
    val latencyMs: Long,
    val errorMessage: String? = null,
)

private data class CachedAnimeGoMirrorVerification(
    val evaluation: AnimeGoMirrorEvaluation,
    val timestampMs: Long,
)

public object AnimeGoMirrorSelector {
    private val logger = Logger.withTag("AnimeGoMirrorSelector")
    private const val CACHE_TTL_MS = 10 * 60 * 1000L // 10 minutes

    public const val DEFAULT_USER_AGENT: String =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

    public val candidateMirrors: List<String> =
        listOf(
            "https://animego.me/",
            "https://animego.org/",
        )

    private val verificationCache = ConcurrentMap<String, CachedAnimeGoMirrorVerification>()

    public fun normalizeMirrorUrl(rawUrl: String): String {
        var clean = rawUrl.trim()
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            clean = "https://$clean"
        }
        if (!clean.endsWith("/")) {
            clean = "$clean/"
        }
        return clean
    }

    public suspend fun evaluateMirror(
        httpClient: HttpClient,
        mirrorUrl: String,
        forceRefresh: Boolean = false,
    ): AnimeGoMirrorEvaluation {
        val normalized = normalizeMirrorUrl(mirrorUrl)
        val now = kotlin.time.Clock.System
            .now()
            .toEpochMilliseconds()

        if (!forceRefresh) {
            val cached = verificationCache[normalized]
            if (cached != null && (now - cached.timestampMs) < CACHE_TTL_MS) {
                return cached.evaluation
            }
        }

        val mark = TimeSource.Monotonic.markNow()
        val evaluation =
            try {
                val targetUrl = "${normalized}search/anime?q=Horimiya"
                val response =
                    httpClient.get(targetUrl) {
                        header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        header("User-Agent", DEFAULT_USER_AGENT)
                    }

                val latency = mark.elapsedNow().inWholeMilliseconds
                if (response.status == HttpStatusCode.OK) {
                    val body = response.bodyAsText()
                    if (body.contains("/anime/")) {
                        AnimeGoMirrorEvaluation(
                            mirrorUrl = normalized,
                            isOnline = true,
                            latencyMs = latency,
                        )
                    } else {
                        AnimeGoMirrorEvaluation(
                            mirrorUrl = normalized,
                            isOnline = false,
                            latencyMs = latency,
                            errorMessage = "Unexpected response payload",
                        )
                    }
                } else {
                    AnimeGoMirrorEvaluation(
                        mirrorUrl = normalized,
                        isOnline = false,
                        latencyMs = latency,
                        errorMessage = "HTTP ${response.status.value}",
                    )
                }
            } catch (e: Exception) {
                val latency = mark.elapsedNow().inWholeMilliseconds
                AnimeGoMirrorEvaluation(
                    mirrorUrl = normalized,
                    isOnline = false,
                    latencyMs = latency,
                    errorMessage = e.message ?: "Network error",
                )
            }

        verificationCache[normalized] = CachedAnimeGoMirrorVerification(evaluation, now)
        return evaluation
    }

    public suspend fun selectBestMirror(
        httpClient: HttpClient,
        preferredMirror: String? = null,
        rankedMirrors: List<String> = emptyList(),
    ): String =
        coroutineScope {
            val candidates = mutableListOf<String>()
            preferredMirror?.takeIf { it.isNotBlank() }?.let { candidates.add(normalizeMirrorUrl(it)) }
            rankedMirrors.filter { it.isNotBlank() }.forEach { candidates.add(normalizeMirrorUrl(it)) }
            candidateMirrors.forEach { candidates.add(it) }

            val uniqueCandidates = candidates.distinct()

            val evaluations =
                uniqueCandidates
                    .map { mirror ->
                        async { evaluateMirror(httpClient, mirror) }
                    }.awaitAll()

            val best =
                evaluations
                    .filter { it.isOnline }
                    .minByOrNull { it.latencyMs }
                    ?.mirrorUrl

            if (best != null) {
                logger.d { "Selected best AnimeGO mirror: $best" }
                best
            } else {
                val fallback = uniqueCandidates.firstOrNull() ?: candidateMirrors.first()
                logger.w { "No responsive AnimeGO mirrors found, falling back to $fallback" }
                fallback
            }
        }
}
