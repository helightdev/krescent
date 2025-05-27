package dev.helight.krescent.event

import dev.helight.krescent.source.StreamingToken
import java.time.Instant

/**
 * Base class for all events in the Krescent framework.
 * Events are immutable and should not be modified after creation.
 */
abstract class Event {

    /**
     * Metadata associated with a **physical** event in the event stream. Virtual events must not specify metadata.
     */
    var metadata: EventMetadata? = null
}

/**
 * Metadata associated with a **physical** event in the event stream. Virtual events must not specify metadata.
 *
 * @property id Unique identifier of the event, usually expected to be in UUIDv4 format.
 * @property type Type identifier of the event
 * @property timestamp Timestamp when this event was created or registered at in the event stream.
 * @property position Position in the event stream, represented by a [StreamingToken].
 */
data class EventMetadata(
    val id: String,
    val type: String,
    val timestamp: Instant,
    val position: StreamingToken<*>,
)

/**
 * Represents an event with no physical representation in the event stream, implying the absence of identity and
 * position in the sourced event stream.
 */
abstract class VirtualEvent() : Event()

/**
 * Flavor of [VirtualEvent] that is used for events emitted by the framework itself.
 * @see VirtualEvent
 */
abstract class SystemEvent : VirtualEvent()

/**
 * Emitted once a writing event model has received an event publication request.
 * This event only implies the intent to publish the event and isn't a publication acknowledgment.
 *
 * Usually, this event will be published on [SystemHintCommitTransactionEvent] if not manually triggered before.
 *
 * This event may be used mainly for **debugging purposes** to track the flow of events through the system and shouldn't
 * have impactful side effects.
 */
class SystemEmittedEvent(
    val event: Event,
) : SystemEvent() {
    override fun toString(): String {
        return event.toString()
    }
}

/**
 * Emitted when an event message is received that does not match any registered event type.
 * This can happen if the event type is not registered in the event catalog.
 */
class SystemUnhandledEventMessageEvent(
    val message: EventMessage,
    val position: StreamingToken<*>,
) : SystemEvent() {
    override fun toString(): String {
        return "SystemUnhandledEventMessageEvent(type=${message.type}, id=${message.id}, position=$position)"
    }
}

/**
 * Emitted once at the start of the stream and can be used to initialize the processor and model states.
 *
 * Example: If a read model wants to project to a table on a remote database, it should create the table or drop
 * existing data after receiving this event since it signals that there are no previous events in the stream, therefore
 * expecting the collection to be in its initial state.
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
 * received. Should only be called by [dev.helight.krescent.source.WriteCompatibleEventSourcingStrategy] implementations.
 */
object SystemHintCommitTransactionEvent : SystemEvent() {
    override fun toString(): String {
        return "SystemHintCommitTransactionEvent"
    }
}

/**
 * Emitted when a [dev.helight.krescent.source.EventSourcingStrategy] initiates a catch-up. Extension and handlers may
 * use replay optimizations after this event has been received to improve performance of the catch-up phase.
 */
object SystemStreamCatchUpEvent : SystemEvent() {
    override fun toString(): String {
        return "SystemStreamCatchUpEvent"
    }
}

/**
 * Emitted when a [dev.helight.krescent.source.EventSourcingStrategy] finished a catch-up operation. Extensions and
 * handlers which use replay optimizations triggered by the [SystemStreamCatchUpEvent] should disable them after this
 * event has been received. If no [SystemStreamTailEvent] is received after this event the stream is still considered live.
 */
object SystemStreamCaughtUpEvent : SystemEvent() {
    override fun toString(): String {
        return "SystemStreamCaughtUpEvent"
    }
}