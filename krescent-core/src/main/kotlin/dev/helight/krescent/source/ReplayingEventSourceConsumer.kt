package dev.helight.krescent.source

import dev.helight.krescent.event.EventMessageStreamProcessor
import dev.helight.krescent.event.SystemStreamHeadEvent

class ReplayingEventSourceConsumer(
    val source: StreamingEventSource,
    val consumer: EventMessageStreamProcessor,
) : EventSourceConsumer {

    override suspend fun strategy(strategy: EventSourcingStrategy) {
        consumer.forwardSystemEvent(SystemStreamHeadEvent)
        strategy.source(source, null, consumer)
    }
}