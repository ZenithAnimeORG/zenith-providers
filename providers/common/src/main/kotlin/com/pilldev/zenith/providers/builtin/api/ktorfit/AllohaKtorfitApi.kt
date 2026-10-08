package com.pilldev.zenith.providers.builtin.api.ktorfit

import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Query
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

public interface AllohaKtorfitApi {
    @GET("/")
    public suspend fun search(
        @Query("token") token: String,
        @Query("kp") kinopoiskId: Int? = null,
        @Query("name") name: String? = null,
        @Query("imdb") imdbId: String? = null,
        @Query("tmdb") tmdbId: Int? = null,
    ): AllohaResponseDto
}

@Serializable
public data class AllohaResponseDto(
    @SerialName("status") val status: String? = null,
    @SerialName("data") val data: AllohaDataDto? = null,
)

@Serializable
public data class AllohaDataDto(
    @SerialName("id") val id: Int? = null,
    @SerialName("id_kp") val idKp: Int? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("original_name") val originalName: String? = null,
    @SerialName("year") val year: Int? = null,
    @SerialName("iframe") val iframe: String? = null,
    @SerialName("seasons") val seasons: Map<String, AllohaSeasonDto>? = null,
)

@Serializable
public data class AllohaSeasonDto(
    @SerialName("episodes") val episodes: Map<String, AllohaEpisodeDto>? = null,
)

@Serializable
public data class AllohaEpisodeDto(
    @SerialName("name") val name: String? = null,
    @SerialName("iframe") val iframe: String? = null,
    @SerialName("translation") val translation: Map<String, AllohaTranslationDto>? = null,
)

@Serializable
public data class AllohaTranslationDto(
    @SerialName("name") val name: String? = null,
    @SerialName("iframe") val iframe: String? = null,
    @SerialName("quality") val quality: String? = null,
)
