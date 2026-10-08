package com.pilldev.zenith.providers.builtin.api.ktorfit

import de.jensklingenberg.ktorfit.http.Field
import de.jensklingenberg.ktorfit.http.FormUrlEncoded
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Header
import de.jensklingenberg.ktorfit.http.Headers
import de.jensklingenberg.ktorfit.http.POST
import de.jensklingenberg.ktorfit.http.Url

public interface HdRezkaKtorfitApi {
    @GET
    public suspend fun search(
        @Url url: String,
        @Header("Referer") referer: String,
    ): String

    @GET
    public suspend fun getPage(
        @Url url: String,
        @Header("Referer") referer: String,
    ): String

    @POST
    @FormUrlEncoded
    @Headers("X-Requested-With: XMLHttpRequest")
    public suspend fun getStreamLinks(
        @Url url: String,
        @Field("id") id: String,
        @Field("translator_id") translatorId: String,
        @Field("season") season: String? = null,
        @Field("episode") episode: String? = null,
        @Field("action") action: String,
        @Header("Referer") referer: String,
        @Header("Origin") origin: String,
    ): String

    @POST
    @FormUrlEncoded
    @Headers("X-Requested-With: XMLHttpRequest")
    public suspend fun getEpisodes(
        @Url url: String,
        @Field("id") id: String,
        @Field("translator_id") translatorId: String,
        @Field("action") action: String = "get_episodes",
        @Header("Referer") referer: String,
    ): String

    @POST
    @FormUrlEncoded
    @Headers("X-Requested-With: XMLHttpRequest")
    public suspend fun loginToMirror(
        @Url url: String,
        @Field("login_name") loginName: String,
        @Field("login_password") loginPassword: String,
        @Field("login") login: String = "submit",
        @Header("Referer") referer: String,
    ): String
}
