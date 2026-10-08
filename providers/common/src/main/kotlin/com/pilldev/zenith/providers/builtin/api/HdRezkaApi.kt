package com.pilldev.zenith.providers.builtin.api

import com.pilldev.zenith.domain.repository.PlayerSettingsRepository
import com.pilldev.zenith.providers.builtin.api.ktorfit.HdRezkaKtorfitApi
import com.pilldev.zenith.providers.builtin.api.ktorfit.createHdRezkaKtorfitApi
import de.jensklingenberg.ktorfit.Ktorfit
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.encodeURLParameter

open class HdRezkaApi
    constructor(
        open val ktorfitApi: HdRezkaKtorfitApi,
        private val playerSettingsManager: PlayerSettingsRepository,
        private val client: HttpClient = HttpClient(),
    ) {
        constructor(
            client: HttpClient,
            playerSettingsManager: PlayerSettingsRepository,
        ) : this(
            Ktorfit
                .Builder()
                .httpClient(client)
                .baseUrl("https://hdrezka.me/")
                .build()
                .createHdRezkaKtorfitApi(),
            playerSettingsManager,
            client,
        )

        private val baseUrl: String
            get() = "https://" + playerSettingsManager.hdRezkaBaseUrl.value
                .ifBlank { "hdrezka.me" }
                .removePrefix("https://")
                .removePrefix("http://")
                .trimEnd('/')

        private fun handleWorkingMirror(mirrorUrl: String) {
            if (mirrorUrl != baseUrl && playerSettingsManager.hdRezkaAutoMirror.value) {
                playerSettingsManager.setHdRezkaBaseUrl(mirrorUrl.removePrefix("https://"))
            }
        }

        private fun getCandidateMirrorsToTry(): List<String> {
            val mirrors = mutableListOf<String>()
            val current = baseUrl
            mirrors.add(current)
            val ranked = playerSettingsManager.hdRezkaRankedMirrors.value
            ranked.forEach { m ->
                val clean = "https://" + m.removePrefix("https://").removePrefix("http://").trimEnd('/')
                if (!mirrors.contains(clean)) mirrors.add(clean)
            }
            val hasLogin = playerSettingsManager.hdRezkaLogin.value.isNotBlank()
            // Account presence is an enhancement, not a replacement:
            // Try AUTH mirrors first, followed immediately by NO_AUTH mirrors as fallback!
            val primary = if (hasLogin) {
                com.pilldev.zenith.domain.model.HdRezkaCandidateMirrors.AUTH
            } else {
                com.pilldev.zenith.domain.model.HdRezkaCandidateMirrors.NO_AUTH
            }
            val secondary = if (hasLogin) {
                com.pilldev.zenith.domain.model.HdRezkaCandidateMirrors.NO_AUTH
            } else {
                com.pilldev.zenith.domain.model.HdRezkaCandidateMirrors.AUTH
            }
            (primary.take(8) + secondary.take(8)).forEach { m ->
                val clean = "https://" + m.removePrefix("https://").removePrefix("http://").trimEnd('/')
                if (!mirrors.contains(clean)) mirrors.add(clean)
            }
            return mirrors
        }

        open suspend fun search(query: String): String {
            val mirrors = getCandidateMirrorsToTry()
            var lastException: Exception? = null
            for (mirrorUrl in mirrors) {
                try {
                    val targetUrl = "$mirrorUrl/search/?do=search&subaction=search&q=${query.encodeURLParameter()}"
                    val html = ktorfitApi.search(url = targetUrl, referer = "$mirrorUrl/")
                    if (com.pilldev.zenith.providers.builtin.parser.HdRezkaAnubisSolver
                            .isChallenge(html)
                    ) {
                        val solved = com.pilldev.zenith.providers.builtin.parser.HdRezkaAnubisSolver
                            .solveAndPass(html, targetUrl, client, "$mirrorUrl/")
                        if (solved) {
                            return ktorfitApi.search(url = targetUrl, referer = "$mirrorUrl/")
                        }
                    }
                    if (html.isNotBlank() && !html.contains("Ошибка доступа") && !html.contains("105")) {
                        handleWorkingMirror(mirrorUrl)
                        return html
                    }
                } catch (e: Exception) {
                    lastException = e
                }
            }
            throw lastException ?: Exception("All HDRezka mirrors failed for search: $query")
        }

        open suspend fun getPage(url: String): String {
            val isAbsolute = url.startsWith("http://") || url.startsWith("https://")
            val relativePath = if (isAbsolute) {
                url.substringAfter("://").substringAfter("/", "")
            } else {
                url.trimStart('/')
            }
            val mirrors = getCandidateMirrorsToTry()
            var lastException: Exception? = null
            for (mirrorUrl in mirrors) {
                try {
                    val targetUrl = "$mirrorUrl/$relativePath"
                    val html = ktorfitApi.getPage(url = targetUrl, referer = "$mirrorUrl/")
                    if (com.pilldev.zenith.providers.builtin.parser.HdRezkaAnubisSolver
                            .isChallenge(html)
                    ) {
                        val solved = com.pilldev.zenith.providers.builtin.parser.HdRezkaAnubisSolver
                            .solveAndPass(html, targetUrl, client, "$mirrorUrl/")
                        if (solved) {
                            return ktorfitApi.getPage(url = targetUrl, referer = "$mirrorUrl/")
                        }
                    }
                    if (html.isNotBlank() && !html.contains("Ошибка доступа") && !html.contains("105")) {
                        handleWorkingMirror(mirrorUrl)
                        return html
                    }
                } catch (e: Exception) {
                    lastException = e
                }
            }
            throw lastException ?: Exception("All HDRezka mirrors failed for getPage: $url")
        }

        open suspend fun getStreamLinks(
            id: String,
            translatorId: String,
            season: String? = null,
            episode: String? = null,
        ): String {
            val mirrors = getCandidateMirrorsToTry()
            var lastException: Exception? = null
            for (mirrorUrl in mirrors) {
                try {
                    val resp = getStreamLinksForMirror(mirrorUrl, id, translatorId, season, episode)
                    if (resp.isNotBlank() && !resp.contains("error_code") && !resp.contains("Ошибка доступа")) {
                        handleWorkingMirror(mirrorUrl)
                        return resp
                    }
                } catch (e: Exception) {
                    lastException = e
                }
            }
            throw lastException ?: Exception("All HDRezka mirrors failed for getStreamLinks id=$id")
        }

        open suspend fun getStreamLinksForMirror(
            mirror: String,
            id: String,
            translatorId: String,
            season: String? = null,
            episode: String? = null,
        ): String {
            val clean = mirror.removePrefix("https://").removePrefix("http://").trimEnd('/')
            val mirrorUrl = "https://$clean"
            val action = if (season != null) "get_stream" else "get_movie"
            val timestamp = kotlin.time.Clock.System
                .now()
                .toEpochMilliseconds()
            val targetUrl = "$mirrorUrl/ajax/get_cdn_series/?t=$timestamp"
            val resp = ktorfitApi.getStreamLinks(
                url = targetUrl,
                id = id,
                translatorId = translatorId,
                season = season,
                episode = episode,
                action = action,
                referer = "$mirrorUrl/",
                origin = mirrorUrl,
            )
            if (com.pilldev.zenith.providers.builtin.parser.HdRezkaAnubisSolver
                    .isChallenge(resp)
            ) {
                val solved = com.pilldev.zenith.providers.builtin.parser.HdRezkaAnubisSolver
                    .solveAndPass(resp, targetUrl, client, "$mirrorUrl/")
                if (solved) {
                    return ktorfitApi.getStreamLinks(
                        url = targetUrl,
                        id = id,
                        translatorId = translatorId,
                        season = season,
                        episode = episode,
                        action = action,
                        referer = "$mirrorUrl/",
                        origin = mirrorUrl,
                    )
                }
            }
            return resp
        }

        open suspend fun getEpisodes(
            id: String,
            translatorId: String,
        ): String {
            val mirrors = getCandidateMirrorsToTry()
            var lastException: Exception? = null
            for (mirrorUrl in mirrors) {
                try {
                    val targetUrl = "$mirrorUrl/ajax/get_cdn_series/"
                    val resp = ktorfitApi.getEpisodes(
                        url = targetUrl,
                        id = id,
                        translatorId = translatorId,
                        referer = "$mirrorUrl/",
                    )
                    if (com.pilldev.zenith.providers.builtin.parser.HdRezkaAnubisSolver
                            .isChallenge(resp)
                    ) {
                        val solved = com.pilldev.zenith.providers.builtin.parser.HdRezkaAnubisSolver
                            .solveAndPass(resp, targetUrl, client, "$mirrorUrl/")
                        if (solved) {
                            return ktorfitApi.getEpisodes(
                                url = targetUrl,
                                id = id,
                                translatorId = translatorId,
                                referer = "$mirrorUrl/",
                            )
                        }
                    }
                    if (resp.isNotBlank() && !resp.contains("error_code") && !resp.contains("Ошибка доступа")) {
                        handleWorkingMirror(mirrorUrl)
                        return resp
                    }
                } catch (e: Exception) {
                    lastException = e
                }
            }
            throw lastException ?: Exception("All HDRezka mirrors failed for getEpisodes id=$id")
        }

        open suspend fun loginToMirror(
            mirror: String,
            loginName: String,
            loginPassword: String,
        ): Boolean {
            val clean = mirror.removePrefix("https://").removePrefix("http://").trimEnd('/')
            val eval = com.pilldev.zenith.providers.builtin.parser.HdRezkaMirrorAuthEvaluator.evaluateAuthMirror(
                httpClient = client,
                mirror = clean,
                loginName = loginName,
                loginPassword = loginPassword,
            )
            val isSuccess = eval.profile?.authStatus == com.pilldev.zenith.domain.model.MirrorAuthStatus.AUTHORIZED || eval.isWorking
            if (isSuccess) {
                val cookies = eval.profile?.authCookies ?: ""
                playerSettingsManager.setHdRezkaMirrorCookies(clean, if (cookies.isNotBlank()) cookies else "dle_user_id=1")
                return true
            } else {
                playerSettingsManager.setHdRezkaMirrorCookies(clean, "FAILED")
                return false
            }
        }

        suspend fun verifyStreamUrl(
            url: String,
            mirror: String,
        ): Boolean =
            try {
                if (url.isBlank()) return false
                val response = client.get(url) {
                    header("Referer", "https://$mirror/")
                    header("User-Agent", com.pilldev.zenith.provider.net.BrowserHeaders.CHROME_DESKTOP_UA)
                    header("Range", "bytes=0-1024")
                }
                val statusCode = response.status.value
                if (statusCode in 200..399) {
                    true
                } else {
                    url.startsWith("http://") || url.startsWith("https://")
                }
            } catch (_: Exception) {
                url.startsWith("http://") || url.startsWith("https://")
            }
    }
