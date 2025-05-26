package dev.helight.krescent.model

import dev.helight.krescent.source.EventSourcingStrategy
import dev.helight.krescent.event.Event
import dev.helight.krescent.event.EventMessageStreamProcessor
import dev.helight.krescent.source.EventSourceConsumer

class EventModel(
    val consumer: EventSourceConsumer,
    val doorstep: EventMessageStreamProcessor,
) {

    private suspend fun runInLifecycle(block: suspend () -> Unit) {
        block()
    }

    /**
     * Streams events continuously from the event source and keeps listening for new events.
     */
    suspend fun stream() = runInLifecycle {
        consumer.stream()
    }

    /**
     * Fetches all historic events from the event source and returns afterward.
     */
    suspend fun catchup() = runInLifecycle {
        consumer.catchup()
    }

    /**
     * Sets the event consumer to its initial state or the last stored checkpoint but doesn't start to resolve events.
     */
    suspend fun restore() = runInLifecycle {
        consumer.restore()
    }

    suspend fun strategy(strategy: EventSourcingStrategy) = runInLifecycle {
        consumer.strategy(strategy)
    }

    @Suppress("unused")
    suspend fun emitSystemEvent(event: Event) {
        doorstep.forwardSystemEvent(event)
    }
}