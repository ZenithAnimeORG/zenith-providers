package com.pilldev.zenith.domain.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

@OptIn(ExperimentalSerializationApi::class)
public object FlexibleStringSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexibleString", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: String?
    ) {
        if (value != null) encoder.encodeString(value) else encoder.encodeNull()
    }

    override fun deserialize(decoder: Decoder): String? {
        val jsonDecoder = decoder as? JsonDecoder ?: return runCatching { decoder.decodeString() }.getOrNull()
        val element = jsonDecoder.decodeJsonElement()
        if (element is JsonNull) return null
        if (element is JsonPrimitive) return element.content
        return element.toString()
    }
}

@OptIn(ExperimentalSerializationApi::class)
public object FlexibleIntSerializer : KSerializer<Int?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexibleInt", PrimitiveKind.INT)

    override fun serialize(
        encoder: Encoder,
        value: Int?
    ) {
        if (value != null) encoder.encodeInt(value) else encoder.encodeNull()
    }

    override fun deserialize(decoder: Decoder): Int? {
        val jsonDecoder = decoder as? JsonDecoder ?: return runCatching { decoder.decodeInt() }.getOrNull()
        val element = jsonDecoder.decodeJsonElement()
        if (element is JsonNull) return null
        if (element is JsonPrimitive) return element.intOrNull ?: element.content.toIntOrNull()
        return null
    }
}

@OptIn(ExperimentalSerializationApi::class)
public object FlexibleDoubleSerializer : KSerializer<Double?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexibleDouble", PrimitiveKind.DOUBLE)

    override fun serialize(
        encoder: Encoder,
        value: Double?
    ) {
        if (value != null) encoder.encodeDouble(value) else encoder.encodeNull()
    }

    override fun deserialize(decoder: Decoder): Double? {
        val jsonDecoder = decoder as? JsonDecoder ?: return runCatching { decoder.decodeDouble() }.getOrNull()
        val element = jsonDecoder.decodeJsonElement()
        if (element is JsonNull) return null
        if (element is JsonPrimitive) return element.doubleOrNull ?: element.content.toDoubleOrNull()
        return null
    }
}

@OptIn(ExperimentalSerializationApi::class)
public object FlexibleStringMapSerializer : KSerializer<Map<String, String>?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexibleStringMap", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: Map<String, String>?
    ) {
        if (value != null) {
            val mapSerializer = MapSerializer(String.serializer(), String.serializer())
            encoder.encodeSerializableValue(mapSerializer, value)
        } else {
            encoder.encodeNull()
        }
    }

    override fun deserialize(decoder: Decoder): Map<String, String>? {
        val jsonDecoder = decoder as? JsonDecoder ?: return null
        val element = jsonDecoder.decodeJsonElement()
        if (element is JsonObject) {
            val map = mutableMapOf<String, String>()
            element.forEach { (k, v) ->
                if (v is JsonPrimitive) {
                    map[k] = v.content
                }
            }
            return map
        }
        return null
    }
}
