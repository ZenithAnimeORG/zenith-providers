package com.pilldev.zenith.providers.builtin.api

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject

@Serializable
public data class AniLibertyRelease(
    val id: Int = 0,
    val name: AniLibertyName? = null,
    val poster: AniLibertyPoster? = null,
    @SerialName("episodes") val episodes: List<AniLibertyEpisode>? = null,
    @Serializable(with = FlexibleAniLibriaTorrentListSerializer::class)
    @SerialName("torrents") val torrents: AniLibriaTorrentList? = null,
)

@Serializable
public data class AniLibriaTorrentList(
    val list: List<AniLibriaTorrent> = emptyList(),
)

@OptIn(ExperimentalSerializationApi::class)
public object FlexibleAniLibriaTorrentListSerializer : KSerializer<AniLibriaTorrentList?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleAniLibriaTorrentList", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: AniLibriaTorrentList?
    ) {
        if (value != null) {
            encoder.encodeSerializableValue(AniLibriaTorrentList.serializer(), value)
        } else {
            encoder.encodeNull()
        }
    }

    override fun deserialize(decoder: Decoder): AniLibriaTorrentList? {
        val jsonDecoder = decoder as? JsonDecoder ?: return null
        val element = jsonDecoder.decodeJsonElement()
        return when (element) {
            is JsonArray -> {
                val list = element.mapNotNull {
                    runCatching { jsonDecoder.json.decodeFromJsonElement(AniLibriaTorrent.serializer(), it) }.getOrNull()
                }
                AniLibriaTorrentList(list)
            }
            is JsonObject -> {
                val listEl = element["list"]
                if (listEl is JsonArray) {
                    val list = listEl.mapNotNull {
                        runCatching { jsonDecoder.json.decodeFromJsonElement(AniLibriaTorrent.serializer(), it) }.getOrNull()
                    }
                    AniLibriaTorrentList(list)
                } else {
                    runCatching { jsonDecoder.json.decodeFromJsonElement(AniLibriaTorrentList.serializer(), element) }.getOrNull()
                }
            }
            else -> null
        }
    }
}

@Serializable
public data class AniLibriaTorrent(
    val torrent_id: Int = 0,
    val hash: String = "",
    val quality: AniLibriaTorrentQuality? = null,
    val series: AniLibriaTorrentSeries? = null,
    val seeders: Int = 0,
    val leechers: Int = 0,
    val total_size: Long = 0,
    val url: String = "",
)

@Serializable
public data class AniLibriaTorrentQuality(
    val string: String = "",
)

@Serializable
public data class AniLibriaTorrentSeries(
    val string: String = "",
)

@Serializable
public data class AniLibertyPoster(
    val preview: String? = null,
    val thumbnail: String? = null,
    val optimized: AniLibertyPoster? = null,
)

@Serializable
public data class AniLibertyLoginRequest(
    val login: String = "",
    val password: String = "",
)

@Serializable
public data class AniLibertyLoginResponse(
    val token: String = "",
)

@Serializable
public data class AniLibertyName(
    val main: String = "",
    val english: String? = null,
    val alternative: String? = null,
)

@Serializable
public data class AniLibertyEpisode(
    val id: String = "",
    val name: String? = null,
    val ordinal: Float = 0f,
    val hls_480: String? = null,
    val hls_720: String? = null,
    val hls_1080: String? = null,
)

@Serializable
public data class AniLibertyUser(
    val id: Int = 0,
    val nickname: String = "",
    val avatar: AniLibertyPoster? = null,
)
