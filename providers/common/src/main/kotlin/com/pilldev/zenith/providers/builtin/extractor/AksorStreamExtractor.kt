package com.pilldev.zenith.providers.builtin.extractor

import com.pilldev.zenith.provider.StreamExtractor
import com.pilldev.zenith.provider.context.ProviderContext
import com.pilldev.zenith.provider.model.ProviderMediaStream
import com.pilldev.zenith.provider.model.ProviderResult
import com.pilldev.zenith.providers.builtin.resolver.AksorResolver
import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json

public class AksorStreamExtractor(
    private val aksorResolver: AksorResolver,
) : StreamExtractor {
    public constructor(
        httpClient: HttpClient = HttpClient(),
        json: Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        },
    ) : this(AksorResolver(httpClient, json))

    override val name: String = "Aksor"

    override fun canExtract(url: String): Boolean = url.contains("player.aksor.tv") || url.contains("aksor.tv")

    override suspend fun extract(
        url: String,
        referer: String?,
        context: ProviderContext?,
    ): ProviderResult<List<ProviderMediaStream>> =
        try {
            val media = aksorResolver.resolve(url, referer)
            if (media != null) {
                val list = media.qualities
                    .map { (quality, streamUrl) ->
                        ProviderMediaStream(
                            url = streamUrl,
                            quality = quality,
                            headers = media.headers,
                            isHls = streamUrl.contains(".m3u8"),
                        )
                    }.ifEmpty {
                        listOf(
                            ProviderMediaStream(
                                url = media.url,
                                quality = "Auto",
                                headers = media.headers,
                                isHls = media.url.contains(".m3u8"),
                            ),
                        )
                    }
                ProviderResult.Success(list)
            } else {
                ProviderResult.Failure("Aksor resolution failed for $url")
            }
        } catch (e: Throwable) {
            ProviderResult.Failure(e.message ?: "Aksor extraction failed", e)
        }
}
