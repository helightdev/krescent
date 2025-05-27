package dev.helight.krescent.event

import dev.helight.krescent.HandlerChainParticipant

/**
 * Generic interface that all event stream processors / handlers must implement.
 */
fun interface EventStreamProcessor : HandlerChainParticipant {
    /**
     * Processes a single event.
     * @param event The event to process
     */
    suspend fun process(event: Event)
}