package dev.helight.krescent.event

import kotlinx.coroutines.flow.Flow

/**
 * Functional interface for generating virtual event streams from input events.
 *
 * Implementations of this interface are responsible for taking an input event of type [Event] and
 * processing it to produce a stream of virtual events of type [VirtualEvent].
 */
fun interface VirtualEventIngest {

    /**
     * Processes an input event into a flow of virtual events. This may be dependent on the internal state of
     * this implementation and isn't required to be pure.
     */
    suspend fun ingest(event: Event): Flow<VirtualEvent>

}