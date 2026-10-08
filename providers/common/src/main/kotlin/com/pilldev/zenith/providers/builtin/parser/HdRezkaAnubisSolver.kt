package com.pilldev.zenith.providers.builtin.parser

import co.touchlab.kermit.Logger
import com.pilldev.zenith.provider.net.BrowserHeaders
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.ByteString.Companion.encodeUtf8

public object HdRezkaAnubisSolver {
    private val logger = Logger.withTag("HdRezkaAnubis")
    private val json = Json { ignoreUnknownKeys = true }
    private val CHALLENGE_REGEX = Regex("""id=["']anubis_challenge["'][^>]*>(.*?)</(?:script|div)>""", RegexOption.DOT_MATCHES_ALL)
    private val BASE_PREFIX_REGEX = Regex("""id=["']anubis_base_prefix["'][^>]*>(.*?)</(?:script|div)>""", RegexOption.DOT_MATCHES_ALL)

    public fun isChallenge(html: String): Boolean = html.contains("anubis_challenge") || (html.contains("anubis") && html.contains("pass-challenge")) || html.contains("anubis_base_prefix")

    public suspend fun solveAndPass(
        html: String,
        targetUrl: String,
        httpClient: HttpClient,
        referer: String? = null,
        userAgent: String = BrowserHeaders.CHROME_DESKTOP_UA,
    ): Boolean {
        return try {
            val challengeMatch = CHALLENGE_REGEX.find(html) ?: return false
            val challengeJsonStr = challengeMatch.groupValues[1].trim()
            if (challengeJsonStr.isBlank() || challengeJsonStr == "null") return false

            val rootObj = json.decodeFromString<JsonObject>(challengeJsonStr)
            val challengeObj = rootObj["challenge"]?.jsonObject ?: return false
            val rulesObj = rootObj["rules"]?.jsonObject ?: return false

            val randomData = challengeObj["randomData"]?.jsonPrimitive?.content ?: return false
            val difficulty = rulesObj["difficulty"]?.jsonPrimitive?.intOrNull ?: 2
            val challengeId = challengeObj["id"]?.jsonPrimitive?.content ?: return false

            val prefixMatch = BASE_PREFIX_REGEX.find(html)
            val basePrefix = prefixMatch
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
                ?.removeSurrounding("\"") ?: ""

            val targetPrefix = "0".repeat(difficulty)
            var nonce = 0L
            var solutionHash = ""

            while (true) {
                val candidate = "$randomData$nonce".encodeUtf8().sha256().hex()
                if (candidate.startsWith(targetPrefix)) {
                    solutionHash = candidate
                    break
                }
                nonce++
            }

            val host = if (targetUrl.startsWith("http")) {
                val clean = targetUrl.substringAfter("://").substringBefore("/")
                "https://$clean"
            } else {
                "https://hdrezka.me"
            }

            val passEndpoint = "$host$basePrefix/.within.website/x/cmd/anubis/api/pass-challenge"
            val queryParams = "id=${challengeId.encodeURLParameter()}&response=${solutionHash.encodeURLParameter()}&nonce=$nonce&redir=${targetUrl.encodeURLParameter()}&elapsedTime=100"
            val fullPassUrl = "$passEndpoint?$queryParams"

            logger.d { "Solving Anubis PoW challenge (difficulty=$difficulty, nonce=$nonce). Requesting pass endpoint with UA: $userAgent" }

            val response = httpClient.get(fullPassUrl) {
                header("User-Agent", userAgent)
                header("Referer", referer ?: targetUrl)
                header("Accept", "*/*")
            }
            val status = response.status.value
            logger.d { "Anubis pass-challenge response: $status" }
            status in 200..399
        } catch (e: Exception) {
            logger.e(e) { "Failed to solve and pass Anubis challenge" }
            false
        }
    }
}
