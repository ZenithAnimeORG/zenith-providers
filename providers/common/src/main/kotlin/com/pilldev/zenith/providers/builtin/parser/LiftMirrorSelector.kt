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

public data class LiftMirrorEvaluation(
    val mirrorUrl: String,
    val isOnline: Boolean,
    val latencyMs: Long,
    val errorMessage: String? = null,
)

private data class CachedLiftMirrorVerification(
    val evaluation: LiftMirrorEvaluation,
    val timestampMs: Long,
)

public object LiftMirrorSelector {
    private val logger = Logger.withTag("LiftMirrorSelector")
    private const val CACHE_TTL_MS = 10 * 60 * 1000L // 10 minutes

    // Base64 encoded "lift1:lift1" -> "bGlmdDE6bGlmdDE="
    public const val BASIC_AUTH_HEADER: String = "Basic bGlmdDE6bGlmdDE="

    public val candidateMirrors: List<String> =
        listOf(
            "https://api.liftw.ws/",
            "https://api.embandr.ws/",
            "https://api.niteface.ws/",
            "https://api.lateremb.ws/",
            "https://api.faphz.com/",
            "https://api.twemd.ws/",
        )

    private val verificationCache = ConcurrentMap<String, CachedLiftMirrorVerification>()

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
    ): LiftMirrorEvaluation {
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
                val targetUrl = "${normalized}search?q=Horimiya"
                val response =
                    httpClient.get(targetUrl) {
                        header("Authorization", BASIC_AUTH_HEADER)
                        header("Accept", "application/json")
                        header(
                            "User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36",
                        )
                    }

                val latency = mark.elapsedNow().inWholeMilliseconds
                if (response.status == HttpStatusCode.OK) {
                    val body = response.bodyAsText()
                    if (body.contains("items") || body.contains("totalCount")) {
                        LiftMirrorEvaluation(
                            mirrorUrl = normalized,
                            isOnline = true,
                            latencyMs = latency,
                        )
                    } else {
                        LiftMirrorEvaluation(
                            mirrorUrl = normalized,
                            isOnline = false,
                            latencyMs = latency,
                            errorMessage = "Unexpected response payload",
                        )
                    }
                } else {
                    LiftMirrorEvaluation(
                        mirrorUrl = normalized,
                        isOnline = false,
                        latencyMs = latency,
                        errorMessage = "HTTP ${response.status.value}",
                    )
                }
            } catch (e: Exception) {
                val latency = mark.elapsedNow().inWholeMilliseconds
                LiftMirrorEvaluation(
                    mirrorUrl = normalized,
                    isOnline = false,
                    latencyMs = latency,
                    errorMessage = e.message ?: "Network error",
                )
            }

        verificationCache[normalized] = CachedLiftMirrorVerification(evaluation, now)
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
                logger.d { "Selected best Lift mirror: $best" }
                best
            } else {
                val fallback = uniqueCandidates.firstOrNull() ?: candidateMirrors.first()
                logger.w { "No responsive Lift mirrors found, falling back to $fallback" }
                fallback
            }
        }
}
