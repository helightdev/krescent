@file:Suppress("unused")

package dev.helight.krescent.model

import dev.helight.krescent.event.*
import dev.helight.krescent.source.EventPublisher
import dev.helight.krescent.source.EventSourcingStrategy
import dev.helight.krescent.source.StreamingEventSource
import dev.helight.krescent.source.WriteCompatibleEventSourcingStrategy
import dev.helight.krescent.source.strategy.CatchupSourcingStrategy

/**
 * Base class for writing models that provides common extension functions and implements event publication
 * and commit handling.
 */
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
     * Emits an event to the WriteModel's event queue which will be published with the next commit.
     *
     * For all default event sourcing strategies, this model will not receive emitted events in its current
     * lifecycle. Therefore, you must manually update the internal state of the writing model when emitting events.
     */
    suspend fun emitEvent(event: Event) {
        writeExtension.enqueueEvent(event)
    }

    /**
     * Manually commits all events that have been emitted to the WriteModel's event queue.
     * By default, events are committed automatically when receiving a [SystemHintCommitTransactionEvent]
     * that is sent by transaction handling sourcing strategies.
     */
    suspend fun commitEvents() {
        writeExtension.commitEvents()
    }

    object Extension {

        /**
         * Overrides the source of this [WriteModelBase] with the provided [StreamingEventSource].
         */
        fun <M : WriteModelBase> M.withSource(
            source: StreamingEventSource,
        ): M {
            this.source = source
            return this
        }

        /**
         * Overrides the publisher of this [WriteModelBase] with the provided [EventPublisher].
         */
        fun <M : WriteModelBase> M.withPublisher(
            publisher: EventPublisher,
        ): M {
            this.publisher = publisher
            return this
        }

        /**
         * Triggers a catch-up for this [WriteModelBase] and executed the provided block inside the
         * transactional boundaries of this [WriteModelBase], if enforced.
         */
        suspend infix fun <M : WriteModelBase> M.handles(
            block: suspend M.() -> Unit,
        ) = handles(CatchupSourcingStrategy(), block)

        /**
         * Triggers the sourcing operation defined by [strategy] for this [WriteModelBase] inside the
         * transactional boundaries of this [WriteModelBase], if enforced.
         */
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