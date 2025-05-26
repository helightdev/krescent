package dev.helight.krescent.source.strategy

import dev.helight.krescent.event.EventMessageStreamProcessor
import dev.helight.krescent.event.SystemHintBeginTransactionEvent
import dev.helight.krescent.event.SystemHintCommitTransactionEvent
import dev.helight.krescent.event.SystemHintEndTransactionEvent
import dev.helight.krescent.event.SystemStreamTailEvent
import dev.helight.krescent.source.EventSourcingStrategy
import dev.helight.krescent.source.StreamingEventSource
import dev.helight.krescent.source.StreamingToken
import dev.helight.krescent.source.WriteCompatibleEventSourcingStrategy

/**
 * A strategy implementation for sourcing events in a catch-up manner, replaying existing events
 * from a specified starting point in the stream and performing a follow-up action after the replay completes.
 *
 * If the consumer uses a **transaction**, it will begin **at the start** of the catch-up and end after the
 * `then` lambda is executed. If you have a stream expected to be relatively large, you may want to
 * use the [TwoPhaseCatchupSourcingStrategy] instead.
 *
 * **Emitted system events**:
 * - [SystemHintBeginTransactionEvent] at the start of the catch-up.
 * - [SystemStreamTailEvent] after all events have been sourced.
 * - [SystemHintCommitTransactionEvent] after the `then` lambda is executed.
 * - [SystemHintEndTransactionEvent] at the end of the catch-up.
 */
class CatchupSourcingStrategy<T : StreamingToken<T>>(
    override var then: suspend () -> Unit = {}
): EventSourcingStrategy<T>, WriteCompatibleEventSourcingStrategy {
    override suspend fun source(
        source: StreamingEventSource<T>,
        startToken: T?,
        consumer: EventMessageStreamProcessor,
    ) {
        consumer.forwardSystemEvent(SystemHintBeginTransactionEvent)
        try {
            source.fetchEventsAfter(startToken).collect {
                it.forwardTo(consumer)
            }
            consumer.forwardSystemEvent(SystemStreamTailEvent)
            then()
            consumer.forwardSystemEvent(SystemHintCommitTransactionEvent)
        } finally {
            consumer.forwardSystemEvent(SystemHintEndTransactionEvent)
        }
    }
}