package com.pilldev.zenith.providers.builtin.api.ktorfit

import com.pilldev.zenith.providers.builtin.model.AnimeSkipResponse
import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.Header
import de.jensklingenberg.ktorfit.http.Headers
import de.jensklingenberg.ktorfit.http.POST

public interface AnimeSkipKtorfitApi {
    @POST("graphql")
    @Headers("Content-Type: application/json")
    public suspend fun getTimestamps(
        @Header("X-Client-ID") clientId: String,
        @Body body: Map<String, String>,
    ): AnimeSkipResponse
}
