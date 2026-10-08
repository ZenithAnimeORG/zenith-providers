package com.pilldev.zenith.providers.builtin.api.ktorfit

import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Path
import de.jensklingenberg.ktorfit.http.Query
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

public interface AnitypeKtorfitApi {
    @GET("kodik/additional/{shikimori_id}/{episode_num}")
    public suspend fun getAdditionalStreams(
        @Path("shikimori_id") shikimoriId: Int,
        @Path("episode_num") episodeNum: Int,
        @Query("translation") translation: String? = null,
    ): AnitypeStreamResponseDto

    @de.jensklingenberg.ktorfit.http.POST("app2/auth/login")
    public suspend fun login(
        @de.jensklingenberg.ktorfit.http.Body request: com.pilldev.zenith.providers.builtin.api.AnitypeLoginRequest,
    ): com.pilldev.zenith.providers.builtin.api.AnitypeLoginResponse

    @GET("users/me")
    public suspend fun getProfile(): com.pilldev.zenith.providers.builtin.api.AnitypeUserResponse
}

@Serializable
public data class AnitypeStreamResponseDto(
    @SerialName("url") val url: String? = null,
    @SerialName("stream") val stream: String? = null,
    @SerialName("kodik") val kodik: String? = null,
    @SerialName("quality") val quality: String? = null,
)
