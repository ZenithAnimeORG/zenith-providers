package com.pilldev.zenith.providers.builtin.extractor

import com.pilldev.zenith.provider.StreamExtractor
import com.pilldev.zenith.provider.context.ProviderContext
import com.pilldev.zenith.provider.model.ProviderMediaStream
import com.pilldev.zenith.provider.model.ProviderResult
import com.pilldev.zenith.providers.builtin.resolver.KodikResolver
import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json

public class KodikStreamExtractor(
    private val kodikResolver: KodikResolver,
) : StreamExtractor {
    public constructor(
        httpClient: HttpClient = HttpClient(),
        json: Json = Json { ignoreUnknownKeys = true },
    ) : this(KodikResolver(httpClient, json))

    override val name: String = "Kodik"

    override fun canExtract(url: String): Boolean = url.contains("kodik") || url.contains("aniqit") || url.contains("anivod")

    override suspend fun extract(
        url: String,
        referer: String?,
        context: ProviderContext?,
    ): ProviderResult<List<ProviderMediaStream>> =
        try {
            val stream = kodikResolver.resolve(url, referer)
            if (stream != null) {
                val list = stream.qualities
                    .map { (quality, streamUrl) ->
                        ProviderMediaStream(
                            url = streamUrl,
                            quality = quality,
                            headers = stream.headers,
                            isHls = streamUrl.contains(".m3u8"),
                        )
                    }.ifEmpty {
                        listOf(
                            ProviderMediaStream(
                                url = stream.url,
                                quality = stream.resolution,
                                headers = stream.headers,
                                isHls = stream.url.contains(".m3u8"),
                            ),
                        )
                    }
                ProviderResult.Success(list)
            } else {
                ProviderResult.Failure("Kodik resolution failed for $url")
            }
        } catch (e: Throwable) {
            ProviderResult.Failure(e.message ?: "Kodik extraction failed", e)
        }
}
