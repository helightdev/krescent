package dev.helight.krescent.event

import dev.helight.krescent.HandlerChainParticipant

fun interface EventStreamProcessor : HandlerChainParticipant {
    /**
     * Processes a single event.
     *
     * @param event The event to process
     */
    suspend fun process(event: Event)
}