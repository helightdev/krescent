@file:Suppress("unused")

package dev.helight.krescent.model

import dev.helight.krescent.event.*
import dev.helight.krescent.source.EventPublisher
import dev.helight.krescent.source.EventSourcingStrategy
import dev.helight.krescent.source.StreamingEventSource
import dev.helight.krescent.source.WriteCompatibleEventSourcingStrategy
import dev.helight.krescent.source.strategy.CatchupSourcingStrategy

abstract class WriteModelBase(
    namespace: String,
    revision: Int,
    catalog: EventCatalog,
    var source: StreamingEventSource? = null,
    var publisher: EventPublisher? = null,
    configure: suspend EventModelBuilder.() -> Unit = { },
) : EventModelBase(
    namespace, revision, catalog, configure
) {
    private val writeExtension by registerExtension(WriteModelExtension())

    /**
     * Emits an event to the WriteModel's event queue.
     * The events will be emitted on the next commit.
     */
    suspend fun emitEvent(event: Event) {
        writeExtension.enqueueEvent(event)
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
        fun <M : WriteModelBase> M.withSource(
            source: StreamingEventSource,
        ): M {
            this.source = source
            return this
        }

        fun <M : WriteModelBase> M.withPublisher(
            publisher: EventPublisher,
        ): M {
            this.publisher = publisher
            return this
        }

        suspend infix fun <M : WriteModelBase> M.handles(
            block: suspend M.() -> Unit,
        ) = handles(CatchupSourcingStrategy(), block)

        suspend fun <M : WriteModelBase, S> M.handles(
            strategy: S,
            block: suspend M.() -> Unit,
        ) where S: EventSourcingStrategy, S: WriteCompatibleEventSourcingStrategy {
            val finalizedSource = source
            if (finalizedSource == null) {
                error("WriteModelBase must have a source configured before using handles()")
            }

            strategy.then = {
                block()
            }

            @Suppress("UNCHECKED_CAST")
            val model = build(finalizedSource)
            model.strategy(strategy)
        }
    }

    private inner class WriteModelExtension() : ModelExtension<WriteModelExtension>, EventStreamProcessor {
        var model: EventModel? = null
        val eventQueue: ArrayDeque<Event> = ArrayDeque()

        val resolvedPublisher: EventPublisher
            get() = publisher ?: source.let {
                it as? EventPublisher
            }
            ?: error("No event publisher configured, please provide one or use a source that implements EventPublisher.")

        override fun unpack() = this

        override fun modelCreatedCallback(model: EventModel) {
            this.model = model
        }

        suspend fun enqueueEvent(event: Event) {
            eventQueue.add(event)
            model!!.emitSystemEvent(SystemEmittedEvent(event))
        }

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