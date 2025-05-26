package dev.helight.krescent.source

import dev.helight.krescent.event.EventMessage
import dev.helight.krescent.event.EventMessageStreamProcessor

interface EventSourcingStrategy<T : StreamingToken<T>> {
    suspend fun source(
        source: StreamingEventSource<T>,
        startToken: T?,
        consumer: EventMessageStreamProcessor,
    )

    suspend fun Pair<EventMessage, T>.forwardTo(
        consumer: EventMessageStreamProcessor,
    ) = consumer.process(this.first, this.second)
}

interface WriteCompatibleEventSourcingStrategy {
    var then: suspend () -> Unit
}