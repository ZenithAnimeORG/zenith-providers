package com.pilldev.zenith.providers.builtin.resolver

import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.parameters
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class KodikStream(
    val resolution: String,
    val url: String,
    val qualities: Map<String, String>,
    val headers: Map<String, String> = emptyMap(),
)

open class KodikResolver
    constructor(
        private val client: io.ktor.client.HttpClient,
        private val json: Json,
    ) {
        open suspend fun resolve(
            iframeUrl: String,
            referer: String? = null,
            isFallback: Boolean = false,
        ): KodikStream? {
            val url = if (iframeUrl.startsWith("//")) "https:$iframeUrl" else iframeUrl
            val domain = url.substringAfter("://").substringBefore("/")

            co.touchlab.kermit.Logger.withTag("ZenithResolver").d {
                "KodikResolver: resolve START for $url (domain: $domain, isFallback: $isFallback)"
            }

            val response =
                try {
                    client.get(url) {
                        headers {
                            set(
                                HttpHeaders.UserAgent,
                                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
                            )
                            set(HttpHeaders.Referrer, referer ?: url)
                            set(HttpHeaders.AcceptEncoding, "identity")
                        }
                    }
                } catch (e: Exception) {
                    co.touchlab.kermit.Logger
                        .withTag("ZenithResolver")
                        .w(e) { "KodikResolver: Initial GET failed for $domain: ${e.message}" }
                    return if (!isFallback) {
                        val fallbackDomain = if (domain == "kodikplayer.com") "kodik.info" else "kodikplayer.com"
                        resolve(url.replace(domain, fallbackDomain), referer, isFallback = true)
                    } else {
                        null
                    }
                }
            val html = response.bodyAsText()

            val metadata = KodikCipher.extractMetadata(html, url, json)
            if (metadata == null) {
                co.touchlab.kermit.Logger
                    .withTag("ZenithResolver")
                    .w { "KodikResolver: FAILED metadata extraction from html for url: $url (HTML size: ${html.length})" }
                return null
            }

            val cookiesMap = mutableMapOf<String, String>()
            response.headers.getAll("Set-Cookie")?.forEach { cookieHeader ->
                val cookie = cookieHeader.substringBefore(";")
                cookiesMap[cookie.substringBefore("=")] = cookie.substringAfter("=")
            }

            val endpoints = listOf("ftor", "gvi", "kor", "video-links", "get_video", "links")
            var videoResponse: HttpResponse? = null
            val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

            for (endpoint in endpoints) {
                try {
                    val r =
                        client.submitForm(
                            url = "https://$domain/$endpoint",
                            formParameters =
                                parameters {
                                    append("id", metadata.id)
                                    append("type", metadata.type)
                                    append("hash", metadata.hash)
                                    metadata.paramsMap.forEach { (k, v) ->
                                        if (k != "id" && k != "type" && k != "hash") {
                                            append(k, v)
                                        }
                                    }
                                    if (!metadata.paramsMap.containsKey("bad_user")) append("bad_user", "false")
                                    if (!metadata.paramsMap.containsKey("cdn_is_working")) append("cdn_is_working", "true")
                                },
                        ) {
                            headers {
                                set(HttpHeaders.UserAgent, userAgent)
                                set(HttpHeaders.Referrer, url)
                                set(HttpHeaders.Accept, "application/json, text/javascript, */*; q=0.01")
                                set(HttpHeaders.AcceptEncoding, "identity")
                                set("X-Requested-With", "XMLHttpRequest")
                                set(HttpHeaders.Origin, "https://$domain")
                                if (cookiesMap.isNotEmpty()) {
                                    set(HttpHeaders.Cookie, cookiesMap.map { "${it.key}=${it.value}" }.joinToString("; "))
                                }
                            }
                        }
                    if (r.status.value == 200) {
                        val contentType = r.headers["Content-Type"]
                        val body = r.bodyAsText()
                        if ((contentType?.contains("json") == true || body.trim().startsWith("{")) && body.isNotBlank()) {
                            videoResponse = r
                            break
                        }
                    }
                } catch (e: Exception) {
                    co.touchlab.kermit.Logger
                        .withTag("ZenithResolver")
                        .d { "KodikResolver: Endpoint $endpoint failed for $domain: ${e.message}" }
                }
            }

            if (videoResponse == null || videoResponse.status.value != 200) {
                co.touchlab.kermit.Logger
                    .withTag("ZenithResolver")
                    .w { "KodikResolver: No working video endpoints for $domain (status: ${videoResponse?.status?.value})" }
                return if (!isFallback) {
                    val fallbackDomain = if (domain == "kodikplayer.com") "kodik.info" else "kodikplayer.com"
                    resolve(url.replace(domain, fallbackDomain), referer, isFallback = true)
                } else {
                    null
                }
            }

            return runCatching {
                val bodyText = videoResponse.bodyAsText()
                val videoData = json.decodeFromString<JsonObject>(bodyText)
                val linksElement = videoData["links"]
                val links = when (linksElement) {
                    is JsonObject -> linksElement
                    is kotlinx.serialization.json.JsonPrimitive -> json.decodeFromString<JsonObject>(linksElement.content)
                    else -> throw Exception("В ответе Kodik отсутствует поле links")
                }
                val qualitiesMap = mutableMapOf<String, String>()

                links.forEach { (q, variants) ->
                    val src = when (variants) {
                        is kotlinx.serialization.json.JsonArray ->
                            variants
                                .firstOrNull()
                                ?.jsonObject
                                ?.get("src")
                                ?.jsonPrimitive
                                ?.content
                        is JsonObject ->
                            variants["src"]?.jsonPrimitive?.content
                        is kotlinx.serialization.json.JsonPrimitive ->
                            variants.content
                        is kotlinx.serialization.json.JsonNull -> null
                    }
                    if (src != null) {
                        val decoded = KodikCipher.decodeKodik(src)
                        if (decoded.isNotBlank()) {
                            qualitiesMap[q + "p"] = if (decoded.startsWith("//")) "https:$decoded" else decoded
                        }
                    }
                }

                if (qualitiesMap.isNotEmpty()) {
                    val sortedKeys = qualitiesMap.keys.sortedByDescending { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
                    val bestKey = sortedKeys.firstOrNull() ?: ""
                    val bestUrl = qualitiesMap[bestKey] ?: ""
                    val sortedQualitiesMap = sortedKeys
                        .mapNotNull { k ->
                            qualitiesMap[k]?.let { v -> k to v }
                        }.toMap()
                    KodikStream(
                        resolution = bestKey,
                        url = bestUrl,
                        qualities = sortedQualitiesMap,
                        headers = mapOf(
                            "Referer" to "https://$domain/",
                            "User-Agent" to userAgent,
                        ),
                    )
                } else {
                    null
                }
            }.onFailure { e ->
                co.touchlab.kermit.Logger
                    .withTag("ZenithResolver")
                    .w(e) { "KodikResolver: parsing video links failed: ${e.message}" }
            }.getOrNull()
        }
    }
