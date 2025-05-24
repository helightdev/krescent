package dev.helight.krescent

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