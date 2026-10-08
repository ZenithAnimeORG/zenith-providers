package com.pilldev.zenith.providers.builtin.api.ktorfit

import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Path
import de.jensklingenberg.ktorfit.http.Query
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

public interface CollapsKtorfitApi {
    @GET("embed/kp/{kp_id}")
    public suspend fun getEmbedByKp(
        @Path("kp_id") kinopoiskId: Int,
        @Query("token") token: String? = null,
    ): CollapsEmbedResponseDto

    @GET("embed/imdb/{imdb_id}")
    public suspend fun getEmbedByImdb(
        @Path("imdb_id") imdbId: String,
        @Query("token") token: String? = null,
    ): CollapsEmbedResponseDto
}

@Serializable
public data class CollapsEmbedResponseDto(
    @SerialName("success") val success: Boolean = false,
    @SerialName("iframe_url") val iframeUrl: String? = null,
    @SerialName("seasons") val seasons: List<CollapsSeasonDto>? = null,
    @SerialName("quality") val quality: String? = null,
)

@Serializable
public data class CollapsSeasonDto(
    @SerialName("season") val season: Int? = null,
    @SerialName("episodes") val episodes: List<CollapsEpisodeDto>? = null,
)

@Serializable
public data class CollapsEpisodeDto(
    @SerialName("episode") val episode: Int? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("hls_url") val hlsUrl: String? = null,
    @SerialName("iframe_url") val iframeUrl: String? = null,
)
