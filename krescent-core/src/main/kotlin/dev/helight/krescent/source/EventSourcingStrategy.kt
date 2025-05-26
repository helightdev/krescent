package dev.helight.krescent.source

import dev.helight.krescent.event.EventMessage
import dev.helight.krescent.event.EventMessageStreamProcessor

interface EventSourcingStrategy {
    suspend fun source(
        source: StreamingEventSource,
        startToken: StreamingToken<*>?,
        consumer: EventMessageStreamProcessor,
    )

    suspend fun Pair<EventMessage, StreamingToken<*>>.forwardTo(
        consumer: EventMessageStreamProcessor,
    ) = consumer.process(this.first, this.second)
}

interface WriteCompatibleEventSourcingStrategy {
    var then: suspend () -> Unit
}