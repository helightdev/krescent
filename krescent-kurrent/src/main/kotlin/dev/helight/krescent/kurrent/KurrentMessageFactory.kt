package dev.helight.krescent.kurrent

import dev.helight.krescent.event.EventMessage
import io.kurrent.dbclient.EventData
import io.kurrent.dbclient.EventDataBuilder
import io.kurrent.dbclient.RecordedEvent
import kotlinx.datetime.toKotlinInstant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.charset.StandardCharsets
import java.util.*

/**
 * A factory for encoding and decoding event messages to and from the kurrentdb event store format.
 */
object KurrentMessageFactory {

    /**
     * Encodes an EventMessage into an EventData structure for event sourcing systems.
     */
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

    /**
     * Decodes a RecordedEvent from the event store into an EventMessage.
     */
    fun decode(event: RecordedEvent): EventMessage {
        val decodedPayload = Json.Default.parseToJsonElement(
            event.eventData.toString(StandardCharsets.UTF_8)
        )
        return EventMessage(
            id = event.eventId.toString(),
            type = event.eventType,
            timestamp = event.created.toKotlinInstant(),
            payload = decodedPayload,
        )
    }
}