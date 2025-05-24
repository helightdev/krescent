package dev.helight.krescent.event

import dev.helight.krescent.serialization.InstantSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.time.Instant
import java.util.*

/**
 * Represents an event message in the Krescent Event Sourcing system.
 *
 * @property id Unique identifier for the event, which should be a UUIDv4.
 * @property timestamp Timestamp when the event occurred, this may be changed by the store storage engine,
 * and therefore is not guaranteed to be the same as the time of creation.
 * @property type Type of the event
 * @property payload The event data as a JSON structure
 */
@Serializable
data class EventMessage(
    val id: String = UUID.randomUUID().toString(),
    @Serializable(with = InstantSerializer::class)
    val timestamp: Instant = Instant.now(),
    val type: String,
    val payload: JsonElement,
)