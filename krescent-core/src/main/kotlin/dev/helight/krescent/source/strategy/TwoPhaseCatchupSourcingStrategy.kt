package dev.helight.krescent.source.strategy

import dev.helight.krescent.event.*
import dev.helight.krescent.source.EventSourcingStrategy
import dev.helight.krescent.source.StreamingEventSource
import dev.helight.krescent.source.StreamingToken
import dev.helight.krescent.source.WriteCompatibleEventSourcingStrategy

/**
 * A strategy implementation for sourcing events in a two-phase catch-up manner similar to [CatchupSourcingStrategy]
 * that optimizes the time spent in-transaction for larger streams. The then lambda will be executed after the
 * second transactional catchup-phase and before the transaction is committed.
 *
 * **Emitted system events**:
 * - [SystemHintBeginTransactionEvent] before the start of the second catch-up phase.
 * - [SystemStreamTailEvent] after all events have been sourced in the first phase.
 * - [SystemHintCommitTransactionEvent] after the `then` lambda is executed.
 * - [SystemHintEndTransactionEvent] at the end of the catch-up.
 * - [SystemStreamCatchUpEvent] at the start of the first catch-up phase.
 * - [SystemStreamCaughtUpEvent] after the first catch-up phase is completed.
 */
class TwoPhaseCatchupSourcingStrategy(
    override var then: suspend () -> Unit = {}
): EventSourcingStrategy, WriteCompatibleEventSourcingStrategy {
    override suspend fun source(
        source: StreamingEventSource,
        startToken: StreamingToken<*>?,
        consumer: EventMessageStreamProcessor,
    ) {
        // Catchup without a transaction at the start
        var lastToken: StreamingToken<*>? = startToken
        consumer.forwardSystemEvent(SystemStreamCatchUpEvent)
        source.fetchEventsAfter(startToken).collect { (message, position) ->
            lastToken = position
            consumer.process(message, position)
        }
        consumer.forwardSystemEvent(SystemStreamCaughtUpEvent)

        // Fetch only the last changes after the transaction has been started
        consumer.forwardSystemEvent(SystemHintBeginTransactionEvent)
        try {
            source.fetchEventsAfter(lastToken).collect { (message, position) ->
                lastToken = position
                consumer.process(message, position)
            }
            consumer.forwardSystemEvent(SystemStreamTailEvent)

            // Apply then to the live state and then commit and end the transaction
            then()
            consumer.forwardSystemEvent(SystemHintCommitTransactionEvent)
        } finally {
            consumer.forwardSystemEvent(SystemHintEndTransactionEvent)
        }
    }
}