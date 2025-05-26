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
abstract class SystemEvent : VirtualEvent()

/**
 * Represents an event that has been emitted by the system.
 */
class SystemEmittedEvent(
    val event: Event,
) : SystemEvent() {
    override fun toString(): String {
        return event.toString()
    }
}


/**
 * Emitted once at the start of the stream and can be used to initialize the processor state.
 */
object SystemStreamHeadEvent : SystemEvent() {
    override fun toString(): String {
        return "SystemStreamHeadEvent"
    }
}

/**
 * Emitted when the stream is restored from a checkpoint similar to the the [SystemStreamHeadEvent]
 * which is emitted at the start of the stream.
 */
object SystemStreamRestoredEvent : SystemEvent() {
    override fun toString(): String {
        return "SystemStreamRestoredEvent"
    }
}

/**
 * Emitted when the stream has reached the tail position. May only be called for terminal event flows
 * and is used to signal that no more physical events will be emitted.
 */
object SystemStreamTailEvent : SystemEvent() {
    override fun toString(): String {
        return "SystemStreamTailEvent"
    }
}


/**
 * Emitted when a [dev.helight.krescent.source.EventSourcingStrategy] hints that a transaction should begin if
 * the event model defines a transaction boundary.
 */
object SystemHintBeginTransactionEvent : SystemEvent() {
    override fun toString(): String {
        return "SystemHintBeginTransactionEvent"
    }
}

/**
 * Emitted when a [dev.helight.krescent.source.EventSourcingStrategy] hints that a transaction should end if
 * the event model defines a transaction boundary.
 */
object SystemHintEndTransactionEvent : SystemEvent() {
    override fun toString(): String {
        return "SystemHintEndTransactionEvent"
    }
}

/**
 * Emitted when a [dev.helight.krescent.source.EventSourcingStrategy] hints that a transaction should be committed
 * if the event model defines a transaction boundary. New events should be published after this event has been
 * received.
 */
object SystemHintCommitTransactionEvent : SystemEvent() {
    override fun toString(): String {
        return "SystemHintCommitTransactionEvent"
    }
}

object SystemStreamCatchUpEvent : SystemEvent() {
    override fun toString(): String {
        return "SystemStreamCatchUpEvent"
    }
}

object SystemStreamCaughtUpEvent : SystemEvent() {
    override fun toString(): String {
        return "SystemStreamCaughtUpEvent"
    }
}