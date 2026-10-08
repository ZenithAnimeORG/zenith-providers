package com.pilldev.zenith.providers.builtin.api.ktorfit

import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Headers
import de.jensklingenberg.ktorfit.http.POST
import de.jensklingenberg.ktorfit.http.Path
import de.jensklingenberg.ktorfit.http.Query
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

public interface AniLibertyKtorfitApi {
    @GET("anime/catalog/releases")
    public suspend fun getCatalogReleases(
        @Query("f[search]") search: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("page") page: Int? = null,
    ): AniLibertyCatalogResponseDto

    @GET("app/search/releases")
    public suspend fun appSearch(
        @Query("query") query: String,
        @Query("limit") limit: Int? = null,
    ): List<AniLibertyReleaseDto>

    @GET("anime/releases/latest")
    public suspend fun getLatestReleases(
        @Query("limit") limit: Int? = null,
        @Query("include") include: String? = null,
    ): AniLibertyCatalogResponseDto

    @GET("anime/releases/{id_or_alias}")
    public suspend fun getRelease(
        @Path("id_or_alias") idOrAlias: String,
        @Query("include") include: String? = null,
    ): AniLibertyReleaseDto

    @POST("accounts/users/auth/login")
    @Headers("Content-Type: application/json")
    public suspend fun login(
        @Body request: AniLibertyLoginRequestDto,
    ): AniLibertyLoginResponseDto

    @GET("accounts/users/me/profile")
    public suspend fun getProfile(): AniLibertyUserDto
}

@Serializable
public data class AniLibertyCatalogResponseDto(
    @SerialName("data") val data: List<AniLibertyReleaseDto>? = null,
    @SerialName("meta") val meta: AniLibertyMetaDto? = null,
)

@Serializable
public data class AniLibertyMetaDto(
    @SerialName("pagination") val pagination: AniLibertyPaginationDto? = null,
)

@Serializable
public data class AniLibertyPaginationDto(
    @SerialName("total") val total: Int? = null,
    @SerialName("count") val count: Int? = null,
    @SerialName("per_page") val perPage: Int? = null,
    @SerialName("current_page") val currentPage: Int? = null,
    @SerialName("total_pages") val totalPages: Int? = null,
)

@Serializable
public data class AniLibertyReleaseDto(
    @SerialName("id") val id: Int? = null,
    @SerialName("type") val type: AniLibertyTypeDto? = null,
    @SerialName("name") val name: AniLibertyNameDto? = null,
    @SerialName("alias") val alias: String? = null,
    @SerialName("season") val season: AniLibertySeasonDto? = null,
    @SerialName("poster") val poster: AniLibertyPosterDto? = null,
    @SerialName("fresh_at") val freshAt: String? = null,
    @SerialName("episodes_total") val episodesTotal: Int? = null,
    @SerialName("episodes") val episodes: List<AniLibertyEpisodeDto>? = null,
    @SerialName("torrents") val torrents: List<AniLibertyTorrentDto>? = null,
    @SerialName("description") val description: String? = null,
)

@Serializable
public data class AniLibertyTypeDto(
    @SerialName("value") val value: String? = null,
    @SerialName("description") val description: String? = null,
)

@Serializable
public data class AniLibertyNameDto(
    @SerialName("main") val main: String? = null,
    @SerialName("english") val english: String? = null,
    @SerialName("alternative") val alternative: String? = null,
)

@Serializable
public data class AniLibertySeasonDto(
    @SerialName("value") val value: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("year") val year: Int? = null,
)

@Serializable
public data class AniLibertyPosterDto(
    @SerialName("src") val src: String? = null,
    @SerialName("preview") val preview: String? = null,
    @SerialName("thumbnail") val thumbnail: String? = null,
)

@Serializable
public data class AniLibertyEpisodeDto(
    @SerialName("id") val id: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("ordinal") val ordinal: Int? = null,
    @SerialName("opening") val opening: AniLibertyTimeRangeDto? = null,
    @SerialName("ending") val ending: AniLibertyTimeRangeDto? = null,
    @SerialName("hls_480") val hls480: String? = null,
    @SerialName("hls_720") val hls720: String? = null,
    @SerialName("hls_1080") val hls1080: String? = null,
)

@Serializable
public data class AniLibertyTimeRangeDto(
    @SerialName("start") val start: Int? = null,
    @SerialName("stop") val stop: Int? = null,
)

@Serializable
public data class AniLibertyTorrentDto(
    @SerialName("id") val id: Int? = null,
    @SerialName("hash") val hash: String? = null,
    @SerialName("magnet") val magnet: String? = null,
    @SerialName("quality") val quality: AniLibertyTypeDto? = null,
    @SerialName("series") val series: AniLibertyTypeDto? = null,
    @SerialName("size") val size: Long? = null,
    @SerialName("seeders") val seeders: Int? = null,
    @SerialName("leechers") val leechers: Int? = null,
)

@Serializable
public data class AniLibertyLoginRequestDto(
    @SerialName("login") val login: String,
    @SerialName("password") val password: String,
)

@Serializable
public data class AniLibertyLoginResponseDto(
    @SerialName("token") val token: String? = null,
)

@Serializable
public data class AniLibertyUserDto(
    @SerialName("id") val id: Int? = null,
    @SerialName("login") val login: String? = null,
    @SerialName("nickname") val nickname: String? = null,
    @SerialName("avatar") val avatar: String? = null,
)
