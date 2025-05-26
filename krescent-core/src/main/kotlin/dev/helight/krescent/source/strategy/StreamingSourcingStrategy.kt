package dev.helight.krescent.source.strategy

import dev.helight.krescent.event.EventMessageStreamProcessor
import dev.helight.krescent.source.EventSourcingStrategy
import dev.helight.krescent.source.StreamingEventSource
import dev.helight.krescent.source.StreamingToken

/**
 * A strategy implementation for actively sourcing live events. This strategy will not terminate unless interrupted
 * and is designed for continuous event processing in read models.
 */
class StreamingSourcingStrategy: EventSourcingStrategy {
    override suspend fun source(
        source: StreamingEventSource,
        startToken: StreamingToken<*>?,
        consumer: EventMessageStreamProcessor,
    ) {
        source.streamEvents(startToken).collect {
            it.forwardTo(consumer)
        }
    }
}