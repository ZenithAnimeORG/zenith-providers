package com.pilldev.zenith.providers.builtin.api.ktorfit

import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Query
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

public interface LiftKtorfitApi {
    @GET("search")
    public suspend fun search(
        @Query("q") query: String,
    ): LiftSearchResponseDto

    @GET("playlist")
    public suspend fun getPlaylist(
        @Query("id") id: String,
    ): LiftPlaylistResponseDto
}

@Serializable
public data class LiftSearchResponseDto(
    @SerialName("totalCount") val totalCount: Int? = null,
    @SerialName("items") val items: List<LiftSearchItemDto> = emptyList(),
)

@Serializable
public data class LiftSearchItemDto(
    @SerialName("id") val id: Long,
    @SerialName("type") val type: Int? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("origin_name") val originName: String? = null,
    @SerialName("poster") val poster: String? = null,
    @SerialName("year") val year: Int? = null,
    @SerialName("serial_status") val serialStatus: String? = null,
    @SerialName("imdb_rating") val imdbRating: Double? = null,
    @SerialName("kp_rating") val kpRating: Double? = null,
    @SerialName("quality") val quality: String? = null,
)

@Serializable
public data class LiftPlaylistResponseDto(
    @SerialName("title") val title: String? = null,
    @SerialName("franchise_id") val franchiseId: Long? = null,
    @SerialName("video_key") val videoKey: Long? = null,
    @SerialName("video_domains") val videoDomains: List<String> = emptyList(),
    @SerialName("seasons") val seasons: List<LiftSeasonDto>? = null,
    @SerialName("streams") val streams: LiftStreamsDto? = null,
    @SerialName("audio") val audio: LiftAudioDto? = null,
    @SerialName("cc") val subtitles: List<LiftSubtitleDto> = emptyList(),
)

@Serializable
public data class LiftSeasonDto(
    @SerialName("season") val season: Int = 1,
    @SerialName("episodes") val episodes: List<LiftEpisodeDto> = emptyList(),
)

@Serializable
public data class LiftEpisodeDto(
    @SerialName("episode") val episode: String = "1",
    @SerialName("episode_label") val episodeLabel: String? = null,
    @SerialName("id") val id: Long? = null,
    @SerialName("video_key") val videoKey: Long? = null,
    @SerialName("streams") val streams: LiftStreamsDto? = null,
    @SerialName("audio") val audio: LiftAudioDto? = null,
    @SerialName("cc") val subtitles: List<LiftSubtitleDto> = emptyList(),
    @SerialName("duration") val duration: Int? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("name") val name: String? = null,
)

@Serializable
public data class LiftStreamsDto(
    @SerialName("hls") val hls: String? = null,
    @SerialName("dash_vp9") val dashVp9: String? = null,
)

@Serializable
public data class LiftAudioDto(
    @SerialName("names") val names: List<String> = emptyList(),
    @SerialName("order") val order: List<Int> = emptyList(),
    @SerialName("visible") val visible: List<Boolean> = emptyList(),
)

@Serializable
public data class LiftSubtitleDto(
    @SerialName("url") val url: String,
    @SerialName("name") val name: String? = null,
)
