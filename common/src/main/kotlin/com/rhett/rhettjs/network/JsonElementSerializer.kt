package com.rhett.rhettjs.network

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

/**
 * Custom serializer for JsonElement to handle arbitrary JSON data.
 * Allows flexible serialization of any JSON-compatible data structure.
 */
object JsonElementSerializer : KSerializer<JsonElement> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("JsonElement")

    override fun serialize(encoder: Encoder, value: JsonElement) {
        JsonElement.serializer().serialize(encoder, value)
    }

    override fun deserialize(decoder: Decoder): JsonElement {
        return JsonElement.serializer().deserialize(decoder)
    }
}

/**
 * Custom serializer for Map<String, Any?> to JsonElement.
 * Allows RhettPacket to accept plain Kotlin types and serialize them.
 */
object JsonElementMapSerializer : KSerializer<Map<String, Any?>> {
    override val descriptor: SerialDescriptor = MapSerializer(String.serializer(), JsonElement.serializer()).descriptor

    override fun serialize(encoder: Encoder, value: Map<String, Any?>) {
        // Convert map values to JsonElement
        val jsonMap = value.mapValues { it.value.toJsonElement() }
        encoder.encodeSerializableValue(MapSerializer(String.serializer(), JsonElement.serializer()), jsonMap)
    }

    override fun deserialize(decoder: Decoder): Map<String, Any?> {
        // Decode as JsonElement map, then convert back to Any?
        val jsonMap = decoder.decodeSerializableValue(MapSerializer(String.serializer(), JsonElement.serializer()))
        return jsonMap.mapValues { it.value.toKotlinType() }
    }
}

/**
 * Convert any value to JsonElement.
 * Handles primitives, collections, maps, and null.
 *
 * @param value Value to convert
 * @return JsonElement representation
 */
fun Any?.toJsonElement(): JsonElement = when (this) {
    null -> JsonNull
    is JsonElement -> this
    is Number -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    is String -> JsonPrimitive(this)
    is Map<*, *> -> {
        val map = this as Map<*, *>
        JsonObject(map.entries.associate {
            it.key.toString() to it.value.toJsonElement()
        })
    }
    is List<*> -> JsonArray(this.map { it.toJsonElement() })
    is Array<*> -> JsonArray(this.map { it.toJsonElement() })
    else -> JsonPrimitive(this.toString())
}

/**
 * Convert JsonElement back to Kotlin types.
 * Converts JsonPrimitive, JsonObject, JsonArray to standard Kotlin types.
 *
 * @return Kotlin representation (String, Number, Boolean, Map, List, or null)
 */
fun JsonElement.toKotlinType(): Any? = when (this) {
    is JsonNull -> null
    is JsonPrimitive -> {
        when {
            this.isString -> this.content
            this.content == "true" || this.content == "false" -> this.content.toBoolean()
            this.content.contains('.') -> this.content.toDoubleOrNull() ?: this.content
            else -> this.content.toLongOrNull() ?: this.content
        }
    }
    is JsonObject -> this.mapValues { it.value.toKotlinType() }
    is JsonArray -> this.map { it.toKotlinType() }
}
