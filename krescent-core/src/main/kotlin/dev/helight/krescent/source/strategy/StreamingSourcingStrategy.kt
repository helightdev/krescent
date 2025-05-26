package dev.helight.krescent.source.strategy

import dev.helight.krescent.event.EventMessageStreamProcessor
import dev.helight.krescent.event.SystemStreamCatchUpEvent
import dev.helight.krescent.event.SystemStreamCaughtUpEvent
import dev.helight.krescent.source.EventSourcingStrategy
import dev.helight.krescent.source.StreamingEventSource
import dev.helight.krescent.source.StreamingToken

/**
 * A strategy implementation for actively sourcing live events. This strategy will not terminate unless interrupted
 * and is designed for continuous event processing in read models. Before the live stream begins, an initial catch-up
 * is performed to generate catchup events.
 *
 * **Emitted system events**:
 * - [SystemStreamCatchUpEvent] at the start of the catch-up.
 * - [SystemStreamCaughtUpEvent] after the catch-up is completed, before the live stream starts.
 */
class StreamingSourcingStrategy: EventSourcingStrategy {
    override suspend fun source(
        source: StreamingEventSource,
        startToken: StreamingToken<*>?,
        consumer: EventMessageStreamProcessor,
    ) {
        var lastToken: StreamingToken<*>? = startToken
        consumer.forwardSystemEvent(SystemStreamCatchUpEvent)
        source.fetchEventsAfter(lastToken).collect { (message, position) ->
            consumer.process(message, position)
            lastToken = position
        }
        consumer.forwardSystemEvent(SystemStreamCaughtUpEvent)

        source.streamEvents(lastToken).collect {
            it.forwardTo(consumer)
        }
    }
}