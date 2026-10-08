package com.pilldev.zenith.providers.builtin.api.ktorfit

import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Header
import de.jensklingenberg.ktorfit.http.POST
import de.jensklingenberg.ktorfit.http.Query
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

public interface OpenSubtitlesKtorfitApi {
    @GET("subtitles")
    public suspend fun searchSubtitles(
        @Header("Api-Key") apiKey: String,
        @Header("User-Agent") userAgent: String? = null,
        @Query("query") query: String? = null,
        @Query("imdb_id") imdbId: String? = null,
        @Query("tmdb_id") tmdbId: Int? = null,
        @Query("languages") languages: String? = null,
        @Query("season_number") seasonNumber: Int? = null,
        @Query("episode_number") episodeNumber: Int? = null,
        @Query("order_by") orderBy: String? = null,
        @Query("order_direction") orderDirection: String? = null,
    ): OpenSubtitlesSearchResponseDto

    @POST("download")
    public suspend fun requestDownload(
        @Header("Api-Key") apiKey: String,
        @Header("Authorization") authorization: String? = null,
        @Header("User-Agent") userAgent: String? = null,
        @Body request: OpenSubtitlesDownloadRequestDto,
    ): OpenSubtitlesDownloadResponseDto
}

@Serializable
public data class OpenSubtitlesSearchResponseDto(
    @SerialName("total_pages") val totalPages: Int? = null,
    @SerialName("total_count") val totalCount: Int? = null,
    @SerialName("page") val page: Int? = null,
    @SerialName("data") val data: List<OpenSubtitleItemDto>? = null,
)

@Serializable
public data class OpenSubtitleItemDto(
    @SerialName("id") val id: String? = null,
    @SerialName("type") val type: String? = null,
    @SerialName("attributes") val attributes: OpenSubtitleAttributesDto? = null,
)

@Serializable
public data class OpenSubtitleAttributesDto(
    @SerialName("subtitle_id") val subtitleId: String? = null,
    @SerialName("language") val language: String? = null,
    @SerialName("download_count") val downloadCount: Int? = null,
    @SerialName("new_download_count") val newDownloadCount: Int? = null,
    @SerialName("hearing_impaired") val hearingImpaired: Boolean? = null,
    @SerialName("hd") val hd: Boolean? = null,
    @SerialName("format") val format: String? = null,
    @SerialName("fps") val fps: Double? = null,
    @SerialName("votes") val votes: Int? = null,
    @SerialName("points") val points: Int? = null,
    @SerialName("ratings") val ratings: Double? = null,
    @SerialName("from_trusted") val fromTrusted: Boolean? = null,
    @SerialName("foreign_parts_only") val foreignPartsOnly: Boolean? = null,
    @SerialName("ai_translated") val aiTranslated: Boolean? = null,
    @SerialName("machine_translated") val machineTranslated: Boolean? = null,
    @SerialName("release") val release: String? = null,
    @SerialName("comments") val comments: String? = null,
    @SerialName("url") val url: String? = null,
    @SerialName("files") val files: List<OpenSubtitleFileDto>? = null,
)

@Serializable
public data class OpenSubtitleFileDto(
    @SerialName("file_id") val fileId: Long? = null,
    @SerialName("cd_number") val cdNumber: Int? = null,
    @SerialName("file_name") val fileName: String? = null,
)

@Serializable
public data class OpenSubtitlesDownloadRequestDto(
    @SerialName("file_id") val fileId: Long,
    @SerialName("sub_format") val subFormat: String? = null,
)

@Serializable
public data class OpenSubtitlesDownloadResponseDto(
    @SerialName("link") val link: String? = null,
    @SerialName("file_name") val fileName: String? = null,
    @SerialName("requests") val requests: Int? = null,
    @SerialName("remaining") val remaining: Int? = null,
    @SerialName("message") val message: String? = null,
    @SerialName("reset_time") val resetTime: String? = null,
    @SerialName("reset_time_utc") val resetTimeUtc: String? = null,
)
