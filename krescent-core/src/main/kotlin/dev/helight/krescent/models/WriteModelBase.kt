@file:Suppress("unused")

package dev.helight.krescent.models

import dev.helight.krescent.event.*
import dev.helight.krescent.source.*
import dev.helight.krescent.source.EventPublisher.Extensions.publishAll
import dev.helight.krescent.source.strategy.CatchupSourcingStrategy

abstract class WriteModelBase(
    namespace: String,
    revision: Int,
    catalog: EventCatalog,
    val source: StreamingEventSource<*>,
    publisher: EventPublisher? = null,
    configure: suspend EventModelBuilder<*>.() -> Unit = { },
) : EventModelBase(
    namespace, revision, catalog, configure
) {
    private val writeExtension by registerExtension(WriteModelExtension(
        eventPublisher = publisher,
    ))

    /**
     * Emits an event to the WriteModel's event queue.
     * The events will be emitted on the next commit.
     */
    fun emitEvent(event: Event) {
        writeExtension.eventQueue.add(event)
    }

    /**
     * Manually commits all events that have been emitted to the WriteModel's event queue.
     * By default, events are committed automatically when receiving a [dev.helight.krescent.event.SystemHintCommitTransactionEvent]
     * that is sent by transaction handling sourcing strategies.
     */
    suspend fun commitEvents() {
        writeExtension.commitEvents()
    }

    object Extension {
        suspend infix fun <T : StreamingToken<T>, M : WriteModelBase> M.handles(
            block: suspend M.() -> Unit,
        ) = handles(CatchupSourcingStrategy<T>(), block)

        suspend fun <T : StreamingToken<T>, M : WriteModelBase, S> M.handles(
            strategy: S,
            block: suspend M.() -> Unit,
        ) where S: EventSourcingStrategy<T>, S: WriteCompatibleEventSourcingStrategy {
            strategy.then = {
                block()
            }

            @Suppress("UNCHECKED_CAST")
            val model = build(source) as EventModel<T>
            model.strategy(strategy)
        }
    }

    private inner class WriteModelExtension(
        val eventPublisher: EventPublisher? = null,
    ) : ModelExtension<WriteModelExtension>, EventStreamProcessor {

        val eventQueue: ArrayDeque<Event> = ArrayDeque()

        val resolvedPublisher: EventPublisher
            get() = eventPublisher ?: source.let {
                it as? EventPublisher
            }
            ?: error("No event publisher configured, please provide one or use a source that implements EventPublisher.")

        override fun unpack() = this

        suspend fun commitEvents() {
            if (eventQueue.isEmpty()) return

            val publisher = resolvedPublisher
            val messageBuffer = mutableListOf<EventMessage>()

            // Convert all queued events to messages before publishing
            // so that encoding errors will not cause partial commits.
            while (eventQueue.isNotEmpty()) {
                val event = eventQueue.removeFirst()
                val message = catalog.create(event)
                messageBuffer.add(message)
            }

            // Write the events to the event stream
            publisher.publishAll(messageBuffer)
        }

        override suspend fun process(event: Event) {
            when (event) {
                is SystemHintCommitTransactionEvent -> {
                    commitEvents()
                }
            }
        }
    }
}

abstract class ReducingWriteModel<S>(
    namespace: String,
    revision: Int,
    catalog: EventCatalog,
    source: StreamingEventSource<*>,
    publisher: EventPublisher? = null,
    configure: suspend EventModelBuilder<*>.() -> Unit = { }
) : WriteModelBase(namespace, revision, catalog, source, publisher, configure) {

    abstract val initialState: S
    private var currentState = initialState
    abstract suspend fun reduce(state: S, event: Event): S

    override suspend fun process(event: Event) {
         currentState = reduce(currentState, event)
    }
}