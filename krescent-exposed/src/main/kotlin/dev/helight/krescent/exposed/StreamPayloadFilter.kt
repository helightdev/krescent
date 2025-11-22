package dev.helight.krescent.exposed

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

class StreamPayloadFilter(
    val values: Map<String, JsonElement>,
) {
    fun toQuery(): String = Json.encodeToString(values)

    companion object {
        fun field(key: String, value: String) = StreamPayloadFilter(mapOf(key to JsonPrimitive(value)))
        fun field(key: String, value: Number) = StreamPayloadFilter(mapOf(key to JsonPrimitive(value)))
        fun field(key: String, value: Boolean) = StreamPayloadFilter(mapOf(key to JsonPrimitive(value)))
    }
}