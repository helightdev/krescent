package dev.helight.krescent.checkpoint

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

class CheckpointBucket(
    val buffer: MutableMap<String, JsonElement>,
) {

    operator fun set(key: String, value: JsonElement) {
        buffer.put(key, value)
    }

    operator fun get(key: String): JsonElement? {
        return buffer[key]
    }


    fun buildJsonObject(): JsonElement = JsonObject(buffer)

    override fun toString(): String = "CheckpointBucket{buffer=$buffer}"

}