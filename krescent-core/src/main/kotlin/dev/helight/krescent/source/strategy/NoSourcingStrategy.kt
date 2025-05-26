package dev.helight.krescent.source.strategy

import dev.helight.krescent.event.EventMessageStreamProcessor
import dev.helight.krescent.event.SystemHintBeginTransactionEvent
import dev.helight.krescent.event.SystemHintCommitTransactionEvent
import dev.helight.krescent.event.SystemHintEndTransactionEvent
import dev.helight.krescent.event.SystemStreamTailEvent
import dev.helight.krescent.source.EventSourcingStrategy
import dev.helight.krescent.source.StreamingEventSource
import dev.helight.krescent.source.StreamingToken

/**
 * A no-op event sourcing strategy that does not source any additional events.
 *
 * **Emitted system events**:
 * - [SystemHintBeginTransactionEvent] at the start of the sourcing.
 * - [SystemStreamTailEvent] immediately after the start, indicating no events to source.
 * - [SystemHintCommitTransactionEvent] after the tail event.
 * - [SystemHintEndTransactionEvent] at the end of the sourcing.
 */
class NoSourcingStrategy: EventSourcingStrategy {
    override suspend fun source(
        source: StreamingEventSource,
        startToken: StreamingToken<*>?,
        consumer: EventMessageStreamProcessor,
    ) {
        consumer.forwardSystemEvent(SystemHintBeginTransactionEvent)
        try {
            // No-op, no events to source
            consumer.forwardSystemEvent(SystemStreamTailEvent)
            consumer.forwardSystemEvent(SystemHintCommitTransactionEvent)
        } finally {
            consumer.forwardSystemEvent(SystemHintEndTransactionEvent)
        }
    }
}