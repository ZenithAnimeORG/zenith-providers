package com.pilldev.zenith.providers.builtin.parser

import co.touchlab.kermit.Logger
import com.pilldev.zenith.domain.model.HdRezkaMirrorProfile
import com.pilldev.zenith.domain.model.MirrorAuthStatus
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.encodeURLParameter
import io.ktor.http.parameters
import kotlin.time.TimeSource

public object HdRezkaMirrorAuthEvaluator {
    private val logger = Logger.withTag("HdRezkaMirrorAuthEvaluator")

    public suspend fun evaluateAuthMirror(
        httpClient: HttpClient,
        mirror: String,
        loginName: String,
        loginPassword: String,
        searchQuery: String = "Bocchi the Rock!",
    ): HdRezkaMirrorEvaluation {
        val cleanHost = mirror.removePrefix("https://").removePrefix("http://").trimEnd('/')
        val mark = TimeSource.Monotonic.markNow()

        if (loginName.isBlank() || loginPassword.isBlank()) {
            return HdRezkaMirrorEvaluation(
                mirror = cleanHost,
                pingMs = 0L,
                isWorking = false,
                hasContent = false,
                errorMessage = "Учетные данные не заданы",
                profile = HdRezkaMirrorProfile(
                    mirror = cleanHost,
                    isAuthWorking = false,
                    authStatus = MirrorAuthStatus.UNAUTHORIZED,
                ),
            )
        }

        return try {
            val loginUrl = "https://$cleanHost/ajax/login/"
            var loginResponse = try {
                httpClient.submitForm(
                    url = loginUrl,
                    formParameters = parameters {
                        append("login_name", loginName)
                        append("login_password", loginPassword)
                        append("login_not_save", "0")
                        append("login", "submit")
                    },
                ) {
                    header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36")
                    header("Referer", "https://$cleanHost/")
                    header("Origin", "https://$cleanHost")
                    header("X-Requested-With", "XMLHttpRequest")
                    header("Accept", "application/json, text/javascript, */*; q=0.01")
                    header("sec-ch-ua", "\"Not(A:Brand\";v=\"99\", \"Google Chrome\";v=\"133\", \"Chromium\";v=\"133\"")
                }
            } catch (re: ResponseException) {
                re.response
            }

            var loginHtml = loginResponse.bodyAsText()
            var loginStatusCode = loginResponse.status.value

            if (HdRezkaAnubisSolver.isChallenge(loginHtml)) {
                val solved = HdRezkaAnubisSolver.solveAndPass(loginHtml, loginUrl, httpClient, "https://$cleanHost/")
                if (solved) {
                    val retry = try {
                        httpClient.submitForm(
                            url = loginUrl,
                            formParameters = parameters {
                                append("login_name", loginName)
                                append("login_password", loginPassword)
                                append("login_not_save", "0")
                                append("login", "submit")
                            },
                        ) {
                            header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36")
                            header("Referer", "https://$cleanHost/")
                            header("Origin", "https://$cleanHost")
                            header("X-Requested-With", "XMLHttpRequest")
                            header("Accept", "application/json, text/javascript, */*; q=0.01")
                        }
                    } catch (re: ResponseException) {
                        re.response
                    }
                    loginResponse = retry
                    loginHtml = retry.bodyAsText()
                    loginStatusCode = retry.status.value
                }
            }

            val loginPageTitle = Regex("<title>(.*?)</title>", RegexOption.IGNORE_CASE)
                .find(loginHtml)
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
            val isLoginGeoBlocked = loginStatusCode == 403 || loginHtml.contains("Ошибка доступа") || loginHtml.contains("105")
            val isLoginAntiBot = HdRezkaAnubisSolver.isChallenge(loginHtml) || loginHtml.contains("Just a moment...")

            val setCookies = loginResponse.headers.getAll(HttpHeaders.SetCookie) ?: emptyList()
            val authCookiesList = mutableListOf<String>()
            setCookies.forEach { h ->
                val cookie = h.substringBefore(";")
                if (cookie.contains("=") && !cookie.contains("deleted")) {
                    authCookiesList.add(cookie.trim())
                }
            }
            val authCookieStr = authCookiesList.joinToString("; ")
            val isAuthSuccess = loginStatusCode in 200..399 && (loginHtml.contains("success") || loginHtml.contains("dle_user_id") || authCookieStr.contains("dle_user_id"))

            if (!isAuthSuccess) {
                val elapsed = mark.elapsedNow().inWholeMilliseconds
                val errorMsg = when {
                    isLoginGeoBlocked -> "HTTP 403 (Ошибка доступа (105))"
                    loginStatusCode == 403 -> "HTTP 403${if (!loginPageTitle.isNullOrBlank()) " ($loginPageTitle)" else ""}"
                    loginStatusCode == 500 -> "HTTP 500 (Internal Server Error)"
                    loginStatusCode == 404 -> "HTTP 404"
                    isLoginAntiBot -> "Блокировка Anubis / Cloudflare PoW"
                    loginStatusCode in 200..399 -> "Неверный логин или пароль"
                    else -> "HTTP $loginStatusCode"
                }
                val profile = HdRezkaMirrorProfile(
                    mirror = cleanHost,
                    authCookies = "",
                    isAuthWorking = false,
                    authStatus = MirrorAuthStatus.FAILED,
                    authPingMs = elapsed,
                    lastTestedTimestamp = kotlin.time.Clock.System
                        .now()
                        .toEpochMilliseconds(),
                )
                return HdRezkaMirrorEvaluation(
                    mirror = cleanHost,
                    pingMs = elapsed,
                    isWorking = false,
                    hasContent = false,
                    isAntiBot = isLoginAntiBot,
                    httpStatus = loginStatusCode,
                    errorMessage = errorMsg,
                    profile = profile,
                )
            }

            val encodedQuery = searchQuery.encodeURLParameter()
            val searchUrl = "https://$cleanHost/search/?do=search&subaction=search&q=$encodedQuery"
            val searchResponse = try {
                httpClient.get(searchUrl) {
                    header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36")
                    header("Referer", "https://$cleanHost/")
                    if (authCookieStr.isNotBlank()) {
                        header(HttpHeaders.Cookie, authCookieStr)
                    }
                    header("sec-ch-ua", "\"Not(A:Brand\";v=\"99\", \"Google Chrome\";v=\"133\", \"Chromium\";v=\"133\"")
                }
            } catch (re: ResponseException) {
                re.response
            }

            val elapsed = mark.elapsedNow().inWholeMilliseconds
            var searchHtml = searchResponse.bodyAsText()
            var statusCode = searchResponse.status.value

            if (HdRezkaAnubisSolver.isChallenge(searchHtml)) {
                val solved = HdRezkaAnubisSolver.solveAndPass(searchHtml, searchUrl, httpClient, "https://$cleanHost/")
                if (solved) {
                    val retry = try {
                        httpClient.get(searchUrl) {
                            header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36")
                            header("Referer", "https://$cleanHost/")
                            if (authCookieStr.isNotBlank()) header(HttpHeaders.Cookie, authCookieStr)
                        }
                    } catch (re: ResponseException) {
                        re.response
                    }
                    searchHtml = retry.bodyAsText()
                    statusCode = retry.status.value
                }
            }

            val pageTitle = Regex("<title>(.*?)</title>", RegexOption.IGNORE_CASE)
                .find(searchHtml)
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
            val isAntiBot = HdRezkaAnubisSolver.isChallenge(searchHtml) || searchHtml.contains("Just a moment...")
            var hasContent = searchHtml.contains("b-content__inline_item") || searchHtml.contains("b-post") || searchHtml.contains("b-content__main")

            if (!hasContent && !isAntiBot && statusCode in 200..399) {
                val rootUrl = "https://$cleanHost/"
                val rootResponse = try {
                    httpClient.get(rootUrl) {
                        header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36")
                        header("Referer", rootUrl)
                        if (authCookieStr.isNotBlank()) header(HttpHeaders.Cookie, authCookieStr)
                    }
                } catch (re: ResponseException) {
                    re.response
                }
                val rootHtml = rootResponse.bodyAsText()
                if (rootHtml.contains("b-content__inline_item") || rootHtml.contains("b-content__inline_items") || rootHtml.contains("b-content__inline_main") || rootHtml.contains("b-post")) {
                    hasContent = true
                    statusCode = rootResponse.status.value
                }
            }

            val isWorking = statusCode in 200..399 && isAuthSuccess && !isAntiBot

            val authStatus = when {
                isWorking -> MirrorAuthStatus.AUTHORIZED
                !isAuthSuccess -> MirrorAuthStatus.FAILED
                else -> MirrorAuthStatus.UNAUTHORIZED
            }

            val errorMsg = when {
                !isWorking && statusCode !in 200..399 -> "HTTP $statusCode${if (!pageTitle.isNullOrBlank()) " ($pageTitle)" else ""}"
                else -> null
            }

            val profile = HdRezkaMirrorProfile(
                mirror = cleanHost,
                authCookies = authCookieStr,
                isAuthWorking = isWorking,
                authStatus = authStatus,
                authPingMs = elapsed,
                lastTestedTimestamp = kotlin.time.Clock.System
                    .now()
                    .toEpochMilliseconds(),
            )

            HdRezkaMirrorEvaluation(
                mirror = cleanHost,
                pingMs = elapsed,
                isWorking = isWorking,
                hasContent = hasContent,
                isAntiBot = isAntiBot,
                httpStatus = statusCode,
                errorMessage = errorMsg,
                profile = profile,
            )
        } catch (e: Exception) {
            val elapsed = mark.elapsedNow().inWholeMilliseconds
            val cleanMsg = e.message ?: e::class.simpleName ?: "Auth probe error"
            logger.d { "Mirror $cleanHost auth probe error: $cleanMsg" }
            HdRezkaMirrorEvaluation(
                mirror = cleanHost,
                pingMs = elapsed,
                isWorking = false,
                hasContent = false,
                isAntiBot = false,
                errorMessage = cleanMsg,
                profile = HdRezkaMirrorProfile(
                    mirror = cleanHost,
                    isAuthWorking = false,
                    authStatus = MirrorAuthStatus.FAILED,
                    authPingMs = elapsed,
                ),
            )
        }
    }
}
