package com.pilldev.zenith.providers.builtin.parser

import co.touchlab.kermit.Logger
import com.pilldev.zenith.domain.repository.HdRezkaParserRepository
import com.pilldev.zenith.provider.net.BrowserHeaders
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import io.ktor.util.collections.ConcurrentMap
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.time.TimeSource

public object HdRezkaMirrorSelector {
    private val logger = Logger.withTag("HdRezkaMirrorSelector")
    private const val CACHE_TTL_MS = 15 * 60 * 1000L // 15 minutes

    private val verificationCache = ConcurrentMap<String, CachedMirrorVerification>()

    public val allCandidateMirrors: List<String> = HDREZKA_CANDIDATE_MIRRORS

    private fun HttpRequestBuilder.probeHeaders(cleanHost: String) {
        header("User-Agent", BrowserHeaders.CHROME_DESKTOP_UA)
        header("Referer", "https://$cleanHost/")
        header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
        header("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
        header("sec-ch-ua", "\"Not(A:Brand\";v=\"99\", \"Google Chrome\";v=\"133\", \"Chromium\";v=\"133\"")
    }

    public suspend fun evaluateMirror(
        httpClient: HttpClient,
        mirror: String,
        searchQuery: String = "Bocchi the Rock!",
        forceRefresh: Boolean = false,
        loginName: String = "",
        loginPassword: String = "",
    ): HdRezkaMirrorEvaluation {
        val cleanHost = mirror.removePrefix("https://").removePrefix("http://").trimEnd('/')
        val now = kotlin.time.Clock.System
            .now()
            .toEpochMilliseconds()
        if (!forceRefresh) {
            val cached = verificationCache[cleanHost]
            if (cached != null && (now - cached.timestampMs) < CACHE_TTL_MS) {
                return cached.evaluation
            }
        }

        val mark = TimeSource.Monotonic.markNow()
        val guestEval = try {
            val encodedQuery = searchQuery.encodeURLParameter()
            val targetUrl = "https://$cleanHost/search/?do=search&subaction=search&q=$encodedQuery"
            val response = try {
                httpClient.get(targetUrl) { probeHeaders(cleanHost) }
            } catch (re: io.ktor.client.plugins.ResponseException) {
                re.response
            }
            val elapsed = mark.elapsedNow().inWholeMilliseconds
            var html = response.bodyAsText()
            var currentStatusCode = response.status.value

            val isChallenge = HdRezkaAnubisSolver.isChallenge(html)
            if (isChallenge) {
                val solved = HdRezkaAnubisSolver.solveAndPass(html, targetUrl, httpClient, "https://$cleanHost/", BrowserHeaders.CHROME_DESKTOP_UA)
                if (solved) {
                    val retryResponse = try {
                        httpClient.get(targetUrl) { probeHeaders(cleanHost) }
                    } catch (re: io.ktor.client.plugins.ResponseException) {
                        re.response
                    }
                    html = retryResponse.bodyAsText()
                    currentStatusCode = retryResponse.status.value
                }
            }

            val pageTitle = Regex("<title>(.*?)</title>", RegexOption.IGNORE_CASE)
                .find(html)
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
            val isAntiBot = isChallenge || html.contains("Проверяем, что вы не бот") || html.contains("cf-browser-verification") || html.contains("Just a moment...") || html.contains("Attention Required!")
            var hasContent = html.contains("b-content__inline_item") || html.contains("b-content__inline_items") || html.contains("b-content__inline_main") || html.contains("b-content__main") || html.contains("b-post")

            if (!hasContent && !isAntiBot) {
                val rootUrl = "https://$cleanHost/"
                val rootResponse = try {
                    httpClient.get(rootUrl) { probeHeaders(cleanHost) }
                } catch (re: io.ktor.client.plugins.ResponseException) {
                    re.response
                }
                val rootHtml = rootResponse.bodyAsText()
                if (rootHtml.contains("b-content__inline_item") || rootHtml.contains("b-content__inline_items") || rootHtml.contains("b-content__inline_main") || rootHtml.contains("b-post")) {
                    hasContent = true
                    currentStatusCode = rootResponse.status.value
                }
            }

            val isWorking = currentStatusCode in 200..399 && (hasContent || (!isAntiBot && html.length > 500 && !html.contains("Domain Parking") && !html.contains("404 Not Found")))
            val errorMsg = if (!isWorking && currentStatusCode !in 200..399) {
                "HTTP $currentStatusCode${if (!pageTitle.isNullOrBlank()) " ($pageTitle)" else ""}"
            } else {
                null
            }

            HdRezkaMirrorEvaluation(
                mirror = cleanHost,
                pingMs = elapsed,
                isWorking = isWorking,
                hasContent = hasContent,
                isAntiBot = isAntiBot,
                httpStatus = currentStatusCode,
                errorMessage = errorMsg,
            )
        } catch (e: Exception) {
            val elapsed = mark.elapsedNow().inWholeMilliseconds
            val cleanMsg = e.message ?: e::class.simpleName ?: "Network error"
            logger.d { "Mirror $cleanHost probe error: $cleanMsg" }
            HdRezkaMirrorEvaluation(
                mirror = cleanHost,
                pingMs = elapsed,
                isWorking = false,
                hasContent = false,
                isAntiBot = false,
                errorMessage = cleanMsg,
            )
        }

        // Dual Session: If credentials provided, execute Auth Pass as an enhancement, not a replacement
        val finalEvaluation = if (loginName.isNotBlank() && loginPassword.isNotBlank()) {
            val authEval = HdRezkaMirrorAuthEvaluator.evaluateAuthMirror(
                httpClient = httpClient,
                mirror = cleanHost,
                loginName = loginName,
                loginPassword = loginPassword,
                searchQuery = searchQuery,
            )
            val effectiveWorking = guestEval.isWorking || authEval.isWorking
            val effectiveContent = guestEval.hasContent || authEval.hasContent
            guestEval.copy(
                isWorking = effectiveWorking,
                hasContent = effectiveContent,
                profile = authEval.profile,
                authEvaluation = authEval,
            )
        } else {
            guestEval
        }

        verificationCache[cleanHost] = CachedMirrorVerification(finalEvaluation, now)
        return finalEvaluation
    }

    public suspend fun discoverAndRankMirrors(
        httpClient: HttpClient,
        currentBaseUrl: String,
        candidatePool: List<String> = allCandidateMirrors,
        forceRefresh: Boolean = false,
        loginName: String = "",
        loginPassword: String = "",
    ): List<HdRezkaMirrorEvaluation> =
        coroutineScope {
            val pool = (listOf(currentBaseUrl) + candidatePool).filter(String::isNotBlank).distinct()
            val evaluations =
                pool
                    .map { mirror ->
                        async {
                            evaluateMirror(
                                httpClient = httpClient,
                                mirror = mirror,
                                forceRefresh = forceRefresh,
                                loginName = loginName,
                                loginPassword = loginPassword,
                            )
                        }
                    }.awaitAll()

            evaluations.sortedWith(
                compareByDescending<HdRezkaMirrorEvaluation> { it.isWorking && it.hasContent }
                    .thenByDescending { it.authEvaluation?.isWorking == true }
                    .thenBy { if (it.isWorking && it.hasContent) it.pingMs else Long.MAX_VALUE }
                    .thenBy { it.mirror },
            )
        }

    public suspend fun verifyMirrorStream(
        mirror: String,
        hdRezkaParser: HdRezkaParserRepository,
    ): Boolean {
        val cleanHost = mirror.removePrefix("https://").removePrefix("http://").trimEnd('/')
        val now = kotlin.time.Clock.System
            .now()
            .toEpochMilliseconds()
        val cached = verificationCache[cleanHost]
        if (cached?.hasValidStream != null && (now - cached.timestampMs) < CACHE_TTL_MS) {
            return cached.hasValidStream
        }

        val isValid = try {
            val sources = hdRezkaParser.getHdRezkaSources(47917, "Bocchi the Rock!", "Одинокий рокер!")
            val targetVoiceover = sources.firstOrNull { s ->
                val tn = s.translationName.lowercase()
                val n = s.name.lowercase()
                tn.contains("anilibria") ||
                    tn.contains("анилибрия") ||
                    tn.contains("дубляж") ||
                    tn.contains("studioband") ||
                    n.contains("anilibria") ||
                    n.contains("анилибрия") ||
                    n.contains("дубляж")
            } ?: sources.firstOrNull()

            val episodeUrl = targetVoiceover?.episodes?.firstOrNull()?.url
            if (episodeUrl.isNullOrBlank()) {
                false
            } else {
                val stream = hdRezkaParser.resolve(episodeUrl)
                if (stream == null || stream.url.isBlank()) {
                    false
                } else {
                    hdRezkaParser.verifyStreamUrl(stream.url, cleanHost)
                }
            }
        } catch (e: Exception) {
            logger.d { "Stream verification failed for mirror $cleanHost: ${e.message}" }
            false
        }

        if (cached != null) {
            verificationCache[cleanHost] = cached.copy(hasValidStream = isValid)
        }
        return isValid
    }

    public suspend fun selectBestMirror(
        httpClient: HttpClient,
        currentBaseUrl: String,
        candidatePool: List<String> = allCandidateMirrors,
        hdRezkaParser: HdRezkaParserRepository? = null,
        forceRefresh: Boolean = false,
        loginName: String = "",
        loginPassword: String = "",
        onMirrorSelected: (String) -> Unit,
        onRankedUpdated: ((List<String>) -> Unit)? = null,
    ): String? {
        val ranked = discoverAndRankMirrors(
            httpClient = httpClient,
            currentBaseUrl = currentBaseUrl,
            candidatePool = candidatePool,
            forceRefresh = forceRefresh,
            loginName = loginName,
            loginPassword = loginPassword,
        )
        val workingWithContent = ranked.filter { it.isWorking && it.hasContent }

        if (workingWithContent.isEmpty()) {
            logger.w { "No valid HDRezka mirrors with content found in pool of ${ranked.size} candidates" }
            return null
        }

        val validatedMirrors = mutableListOf<String>()
        var bestMirror: String? = null

        if (hdRezkaParser != null) {
            for (candidate in workingWithContent) {
                val hasStream = verifyMirrorStream(candidate.mirror, hdRezkaParser)
                if (hasStream) {
                    validatedMirrors.add(candidate.mirror)
                    if (bestMirror == null) {
                        bestMirror = candidate.mirror
                    }
                } else {
                    logger.d { "Mirror ${candidate.mirror} filtered out: stream contains payment stub or is unplayable" }
                }
            }
        } else {
            bestMirror = workingWithContent.first().mirror
            validatedMirrors.addAll(workingWithContent.map { it.mirror })
        }

        if (bestMirror != null) {
            onMirrorSelected(bestMirror)
            if (validatedMirrors.isNotEmpty()) {
                onRankedUpdated?.invoke(validatedMirrors)
            }
            logger.i { "Selected best HDRezka mirror with valid content and playable stream: $bestMirror" }
            return bestMirror
        }

        logger.w { "All ${workingWithContent.size} mirrors failed stream/paywall validation" }
        return null
    }

    public fun clearCache() {
        verificationCache.clear()
    }
}
