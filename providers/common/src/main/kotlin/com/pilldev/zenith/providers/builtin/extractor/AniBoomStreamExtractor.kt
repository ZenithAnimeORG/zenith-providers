package com.pilldev.zenith.providers.builtin.extractor

import com.pilldev.zenith.provider.StreamExtractor
import com.pilldev.zenith.provider.context.ProviderContext
import com.pilldev.zenith.provider.model.ProviderMediaStream
import com.pilldev.zenith.provider.model.ProviderResult
import com.pilldev.zenith.providers.builtin.parser.AniBoomExtractor
import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json

public class AniBoomStreamExtractor(
    private val aniBoomExtractor: AniBoomExtractor,
) : StreamExtractor {
    public constructor(
        httpClient: HttpClient = HttpClient(),
        json: Json = Json { ignoreUnknownKeys = true },
    ) : this(AniBoomExtractor(httpClient, json))

    override val name: String = "AniBoom"

    override fun canExtract(url: String): Boolean = url.contains("aniboom.one") || url.contains("aniboom.")

    override suspend fun extract(
        url: String,
        referer: String?,
        context: ProviderContext?,
    ): ProviderResult<List<ProviderMediaStream>> =
        try {
            val streams = aniBoomExtractor.extractStream(url)
            if (streams.isNotEmpty()) {
                ProviderResult.Success(streams)
            } else {
                ProviderResult.Failure("No streams found for AniBoom URL: $url")
            }
        } catch (e: Throwable) {
            ProviderResult.Failure(e.message ?: "AniBoom extraction failed", e)
        }
}
