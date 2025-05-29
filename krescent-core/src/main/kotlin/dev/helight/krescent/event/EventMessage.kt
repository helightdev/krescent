package dev.helight.krescent.event

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
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
    val timestamp: Instant = Clock.System.now(),
    val type: String,
    val payload: JsonElement,
)