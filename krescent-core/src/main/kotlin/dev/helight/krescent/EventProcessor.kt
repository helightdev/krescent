package dev.helight.krescent

import kotlinx.coroutines.flow.Flow

/**
 * Interface for processing events from an event source.
 * This provides a traditional event consumer approach.
 */
interface EventProcessor {
    /**
     * Processes a single event.
     *
     * @param event The event to process
     */
    suspend fun process(event: EventMessage)
}