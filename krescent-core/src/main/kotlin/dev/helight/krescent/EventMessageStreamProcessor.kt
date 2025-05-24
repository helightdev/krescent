package dev.helight.krescent

import dev.helight.krescent.event.Event
import dev.helight.krescent.event.VirtualEvent
import kotlinx.coroutines.flow.Flow
import java.util.function.Consumer

interface HandlerChainParticipant {

    fun accept(visitor: Consumer<HandlerChainParticipant>) {
        visitor.accept(this)
    }

}

/**
 * Interface for processing events from an event source.
 * This provides a traditional event consumer approach.
 */
fun interface EventMessageStreamProcessor : HandlerChainParticipant {
    /**
     * Processes a single event.
     *
     * @param message The event to process
     */
    suspend fun process(message: EventMessage, position: StreamingToken<*>)
}


fun interface EventStreamProcessor : HandlerChainParticipant {
    /**
     * Processes a single event.
     *
     * @param event The event to process
     */
    suspend fun process(event: Event)
}

fun interface VirtualEventStreamTransformer {

    suspend fun ingest(event: Event): Flow<VirtualEvent>

}