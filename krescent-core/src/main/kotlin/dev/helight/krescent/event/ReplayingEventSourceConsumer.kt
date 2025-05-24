package dev.helight.krescent.event

import dev.helight.krescent.EventMessageStreamProcessor
import dev.helight.krescent.StreamingEventSource
import dev.helight.krescent.StreamingToken

class ReplayingEventSourceConsumer<T : StreamingToken<T>>(
    val source: StreamingEventSource<T>,
    val consumer: EventMessageStreamProcessor,
) : EventSourceConsumer {

    override suspend fun stream() {
        source.streamEvents(null).collect {
            val (message, position) = it
            consumer.process(message, position)
        }
    }

    override suspend fun catchup() {
        source.fetchEventsAfter(null).collect {
            val (message, position) = it
            consumer.process(message, position)
        }
    }
}

