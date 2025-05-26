package dev.helight.krescent.source

import dev.helight.krescent.event.EventMessageStreamProcessor
import dev.helight.krescent.event.SystemStreamHeadEvent

class ReplayingEventSourceConsumer<T : StreamingToken<T>>(
    val source: StreamingEventSource<T>,
    val consumer: EventMessageStreamProcessor,
) : EventSourceConsumer<T> {

    override suspend fun strategy(strategy: EventSourcingStrategy<T>) {
        consumer.forwardSystemEvent(SystemStreamHeadEvent)
        strategy.source(source, null, consumer)
    }
}