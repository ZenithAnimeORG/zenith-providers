package com.pilldev.zenith.providers.builtin.api.ktorfit

import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Path
import de.jensklingenberg.ktorfit.http.Query
import de.jensklingenberg.ktorfit.http.Url
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

public interface AniSkipKtorfitApi {
    @GET("skip-times/{mal_id}/{episode_number}")
    public suspend fun getSkipTimes(
        @Path("mal_id") malId: Int,
        @Path("episode_number") episodeNumber: Int,
        @Query("types[]") types: List<String>? = null,
        @Query("episodeLength") episodeLength: Double? = null,
    ): AniSkipResponseDto

    @GET
    public suspend fun getSkipTimesByUrl(
        @Url url: String,
    ): AniSkipResponseDto
}

public interface AniZipKtorfitApi {
    @GET("mappings")
    public suspend fun getMappings(
        @Query("mal_id") malId: Int? = null,
        @Query("anilist_id") anilistId: Int? = null,
    ): AniZipMappingsDto
}

@Serializable
public data class AniSkipResponseDto(
    @SerialName("found") val found: Boolean = false,
    @SerialName("results") val results: List<AniSkipResultDto>? = null,
    @SerialName("message") val message: String? = null,
    @SerialName("statusCode") val statusCode: Int? = null,
)

@Serializable
public data class AniSkipResultDto(
    @SerialName("interval") val interval: AniSkipIntervalDto? = null,
    @SerialName("skipType") val skipType: String? = null,
    @SerialName("skipId") val skipId: String? = null,
    @SerialName("episodeLength") val episodeLength: Double? = null,
)

@Serializable
public data class AniSkipIntervalDto(
    @SerialName("startTime") val startTime: Double = 0.0,
    @SerialName("endTime") val endTime: Double = 0.0,
)

@Serializable
public data class AniZipMappingsDto(
    @SerialName("titles") val titles: Map<String, String>? = null,
    @SerialName("episodes") val episodes: Map<String, AniZipEpisodeDto>? = null,
    @SerialName("episodeCount") val episodeCount: Int? = null,
    @SerialName("specialCount") val specialCount: Int? = null,
    @SerialName("mappings") val mappings: AniZipMappingsDetailDto? = null,
    @SerialName("images") val images: List<AniZipImageDto>? = null,
)

@Serializable
public data class AniZipMappingsDetailDto(
    @SerialName("mal_id") val malId: Int? = null,
    @SerialName("anidb_id") val anidbId: Int? = null,
    @SerialName("anilist_id") val anilistId: Int? = null,
    @SerialName("thetvdb_id") val thetvdbId: Int? = null,
    @SerialName("themoviedb_id") val themoviedbId: String? = null,
    @SerialName("imdb_id") val imdbId: String? = null,
    @SerialName("type") val type: String? = null,
)

@Serializable
public data class AniZipImageDto(
    @SerialName("coverType") val coverType: String? = null,
    @SerialName("url") val url: String? = null,
)

@Serializable
public data class AniZipEpisodeDto(
    @SerialName("tvdbShowId") val tvdbShowId: Int? = null,
    @SerialName("tvdbId") val tvdbId: Int? = null,
    @SerialName("seasonNumber") val seasonNumber: Int? = null,
    @SerialName("episodeNumber") val episodeNumber: Int? = null,
    @SerialName("absoluteEpisodeNumber") val absoluteEpisodeNumber: Int? = null,
    @SerialName("title") val title: Map<String, String>? = null,
    @SerialName("airDate") val airDate: String? = null,
    @SerialName("length") val length: Int? = null,
    @SerialName("rating") val rating: String? = null,
    @SerialName("image") val image: String? = null,
    @SerialName("overview") val overview: String? = null,
)
