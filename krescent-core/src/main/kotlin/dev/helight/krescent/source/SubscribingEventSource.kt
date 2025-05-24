package dev.helight.krescent.source

import dev.helight.krescent.event.EventMessage
import kotlinx.coroutines.flow.Flow

/**
 * Interface for an event source that provides a subscription to newly published events.
 * Unlike StreamingEventSource, this interface does not support replaying past events.
 */
interface SubscribingEventSource {
    /**
     * Creates a flow that emits new events as they are published.
     *
     * @return A flow of new events
     */
    suspend fun subscribe(): Flow<EventMessage>
}