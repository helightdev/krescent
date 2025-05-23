package dev.helight.krescent

import dev.helight.krescent.serialization.InstantSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.time.Instant

/**
 * Represents an event message in the Krescent Event Sourcing system.
 *
 * @property id Unique identifier for the event
 * @property timestamp When the event occurred
 * @property type Type of the event
 * @property tags Set of tags for dynamic consistency boundaries
 * @property payload The event data as a JSON structure
 */
@Serializable
data class EventMessage(
    val id: String,
    @Serializable(with = InstantSerializer::class)
    val timestamp: Instant,
    val type: String,
    val tags: Set<String> = emptySet(),
    val payload: JsonElement
)
