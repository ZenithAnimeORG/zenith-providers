package com.pilldev.zenith.providers.builtin.api

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

@Serializable
public data class YummySearchResponse(
    val response: List<YummyAnimeResult>? = null,
)

@Serializable
public data class YummyAnimeResult(
    val anime_id: Int = 0,
    val title: String = "",
    val alias: String? = null,
    val remote_ids: YummyRemoteIds? = null,
    @Serializable(with = FlexibleYummyPosterSerializer::class)
    val poster: YummyPoster? = null,
)

@Serializable
public data class YummyRemoteIds(
    val shikimori_id: Int? = null,
)

@Serializable
public data class YummyPoster(
    val fullsize: String? = null,
    val big: String? = null,
    val medium: String? = null,
    val small: String? = null,
)

@OptIn(ExperimentalSerializationApi::class)
public object FlexibleYummyPosterSerializer : KSerializer<YummyPoster?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleYummyPoster", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: YummyPoster?,
    ) {
        if (value == null) {
            encoder.encodeNull()
        } else {
            encoder.encodeSerializableValue(YummyPoster.serializer(), value)
        }
    }

    override fun deserialize(decoder: Decoder): YummyPoster? {
        val jsonDecoder = decoder as? JsonDecoder ?: return null
        val element = jsonDecoder.decodeJsonElement()
        return when {
            element is JsonPrimitive && element.isString -> {
                val str = element.content
                YummyPoster(fullsize = str, big = str, medium = str, small = str)
            }
            else -> {
                runCatching { jsonDecoder.json.decodeFromJsonElement(YummyPoster.serializer(), element) }.getOrNull()
            }
        }
    }
}

@Serializable
public data class YummyVideo(
    val id: Int = 0,
    val video_id: Int = 0,
    val number: JsonElement? = null,
    val name: String? = null,
    val type: String? = null,
    val author: JsonElement? = null,
    val player: String? = null,
    val url: String? = null,
    val iframe_url: String? = null,
    val data: YummyVideoData? = null,
)

@Serializable
public data class YummyVideoData(
    val player: String = "",
    val dubbing: String = "",
)

@Serializable
public data class YummyDetailsResponse(
    val response: YummyAnimeResult? = null,
)
