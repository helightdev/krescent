package dev.helight.krescent.source

import dev.helight.krescent.event.EventMessage
import kotlinx.coroutines.flow.FlowCollector

/**
 * A decorator implementation of the EventPublisher interface that notifies a given channel
 * each time an event is published. This can be useful for triggering side operations or signaling
 * other components when event publishing occurs.
 *
 * @property original The original EventPublisher that handles the actual event publishing.
 * @property collector A channel that receives a signal after an event is successfully published.
 */
class NotifyingEventPublisher(
    val original: EventPublisher,
    val collector: FlowCollector<Unit>,
) : EventPublisher {
    override suspend fun publish(event: EventMessage) {
        original.publish(event)
        collector.emit(Unit)
    }

    override suspend fun publishAll(events: List<EventMessage>) {
        original.publishAll(events)
        collector.emit(Unit)
    }

    companion object {
        fun EventPublisher.channelNotifying(channel: FlowCollector<Unit>): EventPublisher =
            NotifyingEventPublisher(this, channel)
    }
}