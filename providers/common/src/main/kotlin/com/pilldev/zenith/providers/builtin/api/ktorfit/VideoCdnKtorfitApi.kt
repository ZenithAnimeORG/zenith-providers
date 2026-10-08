package com.pilldev.zenith.providers.builtin.api.ktorfit

import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Query
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

public interface VideoCdnKtorfitApi {
    @GET("short")
    public suspend fun searchShort(
        @Query("api_token") apiToken: String,
        @Query("title") title: String? = null,
        @Query("kinopoisk_id") kinopoiskId: Int? = null,
        @Query("imdb_id") imdbId: String? = null,
    ): VideoCdnShortResponseDto

    @GET("anime-tv-series")
    public suspend fun getAnimeSeries(
        @Query("api_token") apiToken: String,
        @Query("query") query: String? = null,
        @Query("kinopoisk_id") kinopoiskId: Int? = null,
        @Query("limit") limit: Int? = null,
        @Query("page") page: Int? = null,
    ): VideoCdnSeriesResponseDto
}

@Serializable
public data class VideoCdnShortResponseDto(
    @SerialName("result") val result: Boolean? = null,
    @SerialName("data") val data: List<VideoCdnShortItemDto>? = null,
)

@Serializable
public data class VideoCdnShortItemDto(
    @SerialName("id") val id: Int? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("orig_title") val origTitle: String? = null,
    @SerialName("kp_id") val kpId: String? = null,
    @SerialName("imdb_id") val imdbId: String? = null,
    @SerialName("iframe_src") val iframeSrc: String? = null,
    @SerialName("qualities") val qualities: List<VideoCdnQualityDto>? = null,
    @SerialName("translations") val translations: List<VideoCdnTranslationDto>? = null,
)

@Serializable
public data class VideoCdnQualityDto(
    @SerialName("id") val id: Int? = null,
    @SerialName("url") val url: String? = null,
    @SerialName("resolution") val resolution: Int? = null,
)

@Serializable
public data class VideoCdnTranslationDto(
    @SerialName("id") val id: Int? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("priority") val priority: Int? = null,
    @SerialName("iframe_src") val iframeSrc: String? = null,
    @SerialName("episodes_count") val episodesCount: Int? = null,
)

@Serializable
public data class VideoCdnSeriesResponseDto(
    @SerialName("result") val result: Boolean? = null,
    @SerialName("data") val data: List<VideoCdnSeriesItemDto>? = null,
    @SerialName("total") val total: Int? = null,
)

@Serializable
public data class VideoCdnSeriesItemDto(
    @SerialName("id") val id: Int? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("orig_title") val origTitle: String? = null,
    @SerialName("kp_id") val kpId: String? = null,
    @SerialName("imdb_id") val imdbId: String? = null,
    @SerialName("iframe_src") val iframeSrc: String? = null,
    @SerialName("episodes") val episodes: List<VideoCdnEpisodeDto>? = null,
)

@Serializable
public data class VideoCdnEpisodeDto(
    @SerialName("id") val id: Int? = null,
    @SerialName("num") val num: String? = null,
    @SerialName("season_id") val seasonId: Int? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("media") val media: List<VideoCdnMediaDto>? = null,
)

@Serializable
public data class VideoCdnMediaDto(
    @SerialName("id") val id: Int? = null,
    @SerialName("translation_id") val translationId: Int? = null,
    @SerialName("max_qualities") val maxQualities: List<Int>? = null,
    @SerialName("path") val path: String? = null,
)
