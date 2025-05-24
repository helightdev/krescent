package dev.helight.krescent.source

import dev.helight.krescent.event.EventMessage

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


    object Extensions {
        suspend fun EventPublisher.publishAll(events: List<EventMessage>) {
            events.forEach { event ->
                publish(event)
            }
        }
    }
}