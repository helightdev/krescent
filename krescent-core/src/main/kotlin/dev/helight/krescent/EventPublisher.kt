package dev.helight.krescent

import kotlinx.serialization.json.JsonElement
import java.time.Instant

/**
 * Interface for publishing events to an event source.
 * This is separate from event consumption interfaces.
 */
interface EventPublisher {
    /**
     * Publishes an event to the event source.
     *
     * @param event The event to publish
     * @return The published event message
     */
    suspend fun publish(event: EventMessage)
}