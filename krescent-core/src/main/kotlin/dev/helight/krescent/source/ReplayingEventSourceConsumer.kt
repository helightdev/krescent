package dev.helight.krescent.source

import dev.helight.krescent.event.EventMessageStreamProcessor
import dev.helight.krescent.event.SystemStreamHeadEvent

class ReplayingEventSourceConsumer<T : StreamingToken<T>>(
    val source: StreamingEventSource<T>,
    val consumer: EventMessageStreamProcessor,
) : EventSourceConsumer {

    override suspend fun stream() {
        consumer.forwardSystemEvent(SystemStreamHeadEvent())
        source.streamEvents(null).collect {
            val (message, position) = it
            consumer.process(message, position)
        }
    }

    override suspend fun catchup() {
        consumer.forwardSystemEvent(SystemStreamHeadEvent())
        source.fetchEventsAfter(null).collect {
            val (message, position) = it
            consumer.process(message, position)
        }
    }

    override suspend fun restore() {
        consumer.forwardSystemEvent(SystemStreamHeadEvent())
    }
}