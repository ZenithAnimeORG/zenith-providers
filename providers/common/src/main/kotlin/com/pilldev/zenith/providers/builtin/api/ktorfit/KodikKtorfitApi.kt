package com.pilldev.zenith.providers.builtin.api.ktorfit

import com.pilldev.zenith.domain.model.FlexibleDoubleSerializer
import com.pilldev.zenith.domain.model.FlexibleIntSerializer
import com.pilldev.zenith.domain.model.FlexibleStringMapSerializer
import com.pilldev.zenith.domain.model.FlexibleStringSerializer
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.POST
import de.jensklingenberg.ktorfit.http.Query
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject

public interface KodikKtorfitApi {
    @GET("search")
    public suspend fun search(
        @Query("token") token: String,
        @Query("title") title: String? = null,
        @Query("title_orig") titleOrig: String? = null,
        @Query("shikimori_id") shikimoriId: Int? = null,
        @Query("kinopoisk_id") kinopoiskId: Int? = null,
        @Query("imdb_id") imdbId: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("with_episodes") withEpisodes: Boolean? = true,
        @Query("with_material_data") withMaterialData: Boolean? = null,
        @Query("strict") strict: Boolean? = null,
        @Query("sort") sort: String? = null,
    ): KodikSearchResponseDto

    @POST("search")
    public suspend fun searchPost(
        @Query("token") token: String,
        @Query("title") title: String? = null,
        @Query("title_orig") titleOrig: String? = null,
        @Query("shikimori_id") shikimoriId: Int? = null,
        @Query("kinopoisk_id") kinopoiskId: Int? = null,
        @Query("imdb_id") imdbId: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("with_episodes") withEpisodes: Boolean? = true,
        @Query("with_material_data") withMaterialData: Boolean? = null,
        @Query("strict") strict: Boolean? = null,
        @Query("sort") sort: String? = null,
    ): KodikSearchResponseDto

    @GET("list")
    public suspend fun list(
        @Query("token") token: String,
        @Query("types") types: String? = null,
        @Query("year") year: Int? = null,
        @Query("limit") limit: Int? = null,
        @Query("with_episodes") withEpisodes: Boolean? = true,
        @Query("with_material_data") withMaterialData: Boolean? = null,
    ): KodikSearchResponseDto

    @GET("translations")
    public suspend fun getTranslations(
        @Query("token") token: String,
        @Query("sort") sort: String? = null,
    ): KodikTranslationsResponseDto
}

@Serializable
public data class KodikSearchResponseDto(
    val time: String? = null,
    val total: Int? = null,
    val results: List<KodikResultDto>? = null,
)

@Serializable
public data class KodikResultDto(
    val id: String? = null,
    val title: String? = null,
    @SerialName("title_orig") val titleOrig: String? = null,
    @SerialName("other_title") val otherTitle: String? = null,
    val link: String? = null,
    @Serializable(with = FlexibleIntSerializer::class) val year: Int? = null,
    val type: String? = null,
    val quality: String? = null,
    val camrip: Boolean? = null,
    val lgbt: Boolean? = null,
    val translation: KodikTranslationDto? = null,
    @SerialName("episodes_count") @Serializable(with = FlexibleIntSerializer::class) val episodesCount: Int? = null,
    @Serializable(with = FlexibleKodikSeasonsSerializer::class) val seasons: Map<String, KodikSeasonDto>? = null,
    @SerialName("material_data") val materialData: KodikMaterialDataDto? = null,
    @SerialName("shikimori_id") @Serializable(with = FlexibleStringSerializer::class) val shikimoriId: String? = null,
    @SerialName("kinopoisk_id") @Serializable(with = FlexibleStringSerializer::class) val kinopoiskId: String? = null,
    @SerialName("imdb_id") @Serializable(with = FlexibleStringSerializer::class) val imdbId: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
public data class KodikTranslationDto(
    @Serializable(with = FlexibleIntSerializer::class) val id: Int? = null,
    val title: String? = null,
    val type: String? = null,
)

@Serializable
public data class KodikSeasonDto(
    @Serializable(with = FlexibleStringMapSerializer::class) val episodes: Map<String, String>? = null,
    val link: String? = null,
)

@OptIn(ExperimentalSerializationApi::class)
public object FlexibleKodikSeasonsSerializer : KSerializer<Map<String, KodikSeasonDto>?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleKodikSeasons", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: Map<String, KodikSeasonDto>?
    ) {
        if (value != null) {
            val mapSerializer = MapSerializer(String.serializer(), KodikSeasonDto.serializer())
            encoder.encodeSerializableValue(mapSerializer, value)
        } else {
            encoder.encodeNull()
        }
    }

    override fun deserialize(decoder: Decoder): Map<String, KodikSeasonDto>? {
        val jsonDecoder = decoder as? JsonDecoder ?: return null
        val element = jsonDecoder.decodeJsonElement()
        if (element is JsonObject) {
            return jsonDecoder.json.decodeFromJsonElement(
                MapSerializer(String.serializer(), KodikSeasonDto.serializer()),
                element,
            )
        }
        return null
    }
}

@Serializable
public data class KodikMaterialDataDto(
    val title: String? = null,
    @SerialName("anime_title") val animeTitle: String? = null,
    @SerialName("title_en") val titleEn: String? = null,
    val description: String? = null,
    @SerialName("poster_url") val posterUrl: String? = null,
    @Serializable(with = FlexibleIntSerializer::class) val duration: Int? = null,
    val genres: List<String>? = null,
    @SerialName("anime_genres") val animeGenres: List<String>? = null,
    @SerialName("anime_studios") val animeStudios: List<String>? = null,
    @SerialName("shikimori_rating") @Serializable(with = FlexibleDoubleSerializer::class) val shikimoriRating: Double? = null,
    @SerialName("shikimori_votes") @Serializable(with = FlexibleIntSerializer::class) val shikimoriVotes: Int? = null,
    @SerialName("episodes_total") @Serializable(with = FlexibleIntSerializer::class) val episodesTotal: Int? = null,
    @SerialName("episodes_aired") @Serializable(with = FlexibleIntSerializer::class) val episodesAired: Int? = null,
    @SerialName("other_titles") val otherTitles: List<String>? = null,
)

@Serializable
public data class KodikTranslationsResponseDto(
    val time: String? = null,
    val total: Int? = null,
    val results: List<KodikTranslationDto>? = null,
)
