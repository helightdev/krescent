package dev.helight.krescent.event

import dev.helight.krescent.source.StreamingToken
import java.time.Instant

abstract class Event {
    var metadata: EventMetadata? = null
}

data class EventMetadata(
    val id: String,
    val type: String,
    val timestamp: Instant,
    val position: StreamingToken<*>?,
)

abstract class VirtualEvent : Event()
/**
 * Emitted once at the start of the stream and can be used to initialize the processor state.
 */
object SystemStreamHeadEvent : VirtualEvent()

/**
 * Emitted when the stream is restored from a checkpoint similar to the the [SystemStreamHeadEvent]
 * which is emitted at the start of the stream.
 */
object SystemStreamRestoredEvent : VirtualEvent()

/**
 * Emitted when the stream has reached the tail position. May only be called for terminal event flows
 * and is used to signal that no more physical events will be emitted.
 */
object SystemStreamTailEvent : VirtualEvent()


/**
 * Emitted when a [dev.helight.krescent.source.EventSourcingStrategy] hints that a transaction should begin if
 * the event model defines a transaction boundary.
 */
object SystemHintBeginTransactionEvent : VirtualEvent()

/**
 * Emitted when a [dev.helight.krescent.source.EventSourcingStrategy] hints that a transaction should end if
 * the event model defines a transaction boundary.
 */
object SystemHintEndTransactionEvent : VirtualEvent()

/**
 * Emitted when a [dev.helight.krescent.source.EventSourcingStrategy] hints that a transaction should be committed
 * if the event model defines a transaction boundary. New events should be published after this event has been
 * received.
 */
object SystemHintCommitTransactionEvent : VirtualEvent()