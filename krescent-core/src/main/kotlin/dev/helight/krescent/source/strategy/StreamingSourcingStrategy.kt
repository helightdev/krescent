package dev.helight.krescent.source.strategy

import dev.helight.krescent.event.*
import dev.helight.krescent.source.EventSourcingStrategy
import dev.helight.krescent.source.StreamingEventSource
import dev.helight.krescent.source.StreamingToken

/**
 * A strategy implementation for actively sourcing live events. This strategy will not terminate unless interrupted
 * and is designed for continuous event processing in read models. Before the live stream begins, an initial catch-up
 * is performed to generate catchup events.
 *
 * This strategy does not really support transactions in the same way as other strategies, as it is designed
 * for continuous streaming of events. However, it will still emit the transaction hints around the catch-up phase, so
 * you can locally prevent multiple read models from replaying at the same time.
 *
 * **Emitted system events**:
 * - [SystemStreamCatchUpEvent] at the start of the catch-up.
 * - [SystemStreamCaughtUpEvent] after the catch-up is completed, before the live stream starts.
 * - [SystemHintBeginTransactionEvent] at the start of the catch-up phase.
 * - [SystemHintEndTransactionEvent] at the end of the catch-up phase, after the live stream has started.
 */
class StreamingSourcingStrategy(
    val afterCatchup: suspend () -> Unit = {},
) : EventSourcingStrategy {
    override suspend fun source(
        source: StreamingEventSource,
        startToken: StreamingToken<*>?,
        consumer: EventMessageStreamProcessor,
    ) {
        var lastToken: StreamingToken<*>? = startToken
        consumer.forwardSystemEvent(SystemHintBeginTransactionEvent)
        try {
            consumer.forwardSystemEvent(SystemStreamCatchUpEvent)
            source.fetchEventsAfter(lastToken).collect { (message, position) ->
                consumer.process(message, position)
                lastToken = position
            }
            consumer.forwardSystemEvent(SystemStreamCaughtUpEvent)
        } finally {
            consumer.forwardSystemEvent(SystemHintEndTransactionEvent)
            afterCatchup()
        }

        // Begin streaming events until interrupted
        source.streamEvents(lastToken).collect {
            it.forwardTo(consumer)
        }
    }
}