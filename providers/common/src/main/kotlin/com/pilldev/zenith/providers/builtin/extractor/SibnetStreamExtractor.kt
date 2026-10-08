package com.pilldev.zenith.providers.builtin.extractor

import com.pilldev.zenith.provider.StreamExtractor
import com.pilldev.zenith.provider.context.ProviderContext
import com.pilldev.zenith.provider.model.ProviderMediaStream
import com.pilldev.zenith.provider.model.ProviderResult
import com.pilldev.zenith.provider.net.BrowserHeaders
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders

public class SibnetStreamExtractor(
    private val httpClient: HttpClient = HttpClient(),
) : StreamExtractor {
    override val name: String = "Sibnet"

    override fun canExtract(url: String): Boolean = url.contains("video.sibnet.ru") || url.contains("sibnet.ru")

    override suspend fun extract(
        url: String,
        referer: String?,
        context: ProviderContext?,
    ): ProviderResult<List<ProviderMediaStream>> =
        try {
            val fullUrl = if (url.startsWith("//")) "https:$url" else url
            val client = context?.httpClient ?: httpClient
            val response =
                client.get(fullUrl) {
                    headers {
                        set(HttpHeaders.UserAgent, BrowserHeaders.CHROME_DESKTOP_UA)
                        set(HttpHeaders.Referrer, "https://video.sibnet.ru/")
                    }
                }
            val html = response.bodyAsText()
            val pattern = Regex("""src:\s*"(/v/[^"]+)"""")
            val match = pattern.find(html)
            val path = match?.groupValues?.getOrNull(1)
            if (path != null) {
                val directUrl = "https://video.sibnet.ru$path"
                ProviderResult.Success(
                    listOf(
                        ProviderMediaStream(
                            url = directUrl,
                            quality = "Auto",
                            headers = mapOf("Referer" to "https://video.sibnet.ru/"),
                            isHls = directUrl.contains(".m3u8"),
                        ),
                    ),
                )
            } else {
                ProviderResult.Failure("Direct stream not found in Sibnet player")
            }
        } catch (e: Throwable) {
            ProviderResult.Failure(e.message ?: "Sibnet extraction failed", e)
        }
}
