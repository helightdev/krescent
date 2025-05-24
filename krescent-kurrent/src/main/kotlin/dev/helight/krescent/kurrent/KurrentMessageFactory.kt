package dev.helight.krescent.kurrent

import dev.helight.krescent.event.EventMessage
import io.kurrent.dbclient.EventData
import io.kurrent.dbclient.EventDataBuilder
import io.kurrent.dbclient.RecordedEvent
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.charset.StandardCharsets
import java.util.*

object KurrentMessageFactory {

    fun encode(message: EventMessage): EventData {
        val metadata = buildJsonObject {
            put("original-timestamp", message.timestamp.toString())
        }

        val metadataBytes = Json.Default
            .encodeToString(metadata)
            .toByteArray(StandardCharsets.UTF_8)
        val dataBytes = Json.Default
            .encodeToString(message.payload)
            .toByteArray(StandardCharsets.UTF_8)

        return EventDataBuilder
            .json(UUID.fromString(message.id), message.type, dataBytes)
            .metadataAsBytes(metadataBytes)
            .build()
    }

    fun decode(event: RecordedEvent): EventMessage {
        val decodedPayload = Json.Default.parseToJsonElement(
            event.eventData.toString(StandardCharsets.UTF_8)
        )
        return EventMessage(
            id = event.eventId.toString(),
            type = event.eventType,
            timestamp = event.created,
            payload = decodedPayload,
        )
    }
}