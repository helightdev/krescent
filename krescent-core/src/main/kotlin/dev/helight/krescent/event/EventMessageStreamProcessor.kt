package dev.helight.krescent.event

import dev.helight.krescent.HandlerChainParticipant
import dev.helight.krescent.source.StreamingToken

/**
 * Interface for processing events from an event source.
 * This provides a traditional event consumer approach.
 */
interface EventMessageStreamProcessor : HandlerChainParticipant {
    /**
     * Processes a single event.
     *
     * @param message The event to process
     */
    suspend fun process(message: EventMessage, position: StreamingToken<*>)

    /**
     * Handles the forwarding of system-produced events.
     */
    suspend fun forwardSystemEvent(event: Event)
}