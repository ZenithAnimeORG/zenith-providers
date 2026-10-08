package com.pilldev.zenith.providers.builtin.api.ktorfit

import com.pilldev.zenith.providers.builtin.api.YummyDetailsResponse
import com.pilldev.zenith.providers.builtin.api.YummySearchResponse
import com.pilldev.zenith.providers.builtin.api.YummyVideo
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Path
import de.jensklingenberg.ktorfit.http.Query
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

public interface YummyKtorfitApi {
    @GET("search")
    public suspend fun search(
        @Query("q") query: String,
        @Query("limit") limit: Int? = null,
        @Query("page") page: Int? = null,
    ): YummySearchResponse

    @GET("anime/{id}")
    public suspend fun getAnime(
        @Path("id") id: Int,
    ): YummyDetailsResponse

    @GET("anime/{id}/videos")
    public suspend fun getVideos(
        @Path("id") id: Int,
    ): YummyVideosResponseDto
}

@Serializable
public data class YummyVideosResponseDto(
    @SerialName("response") val response: List<YummyVideo>? = null,
)
