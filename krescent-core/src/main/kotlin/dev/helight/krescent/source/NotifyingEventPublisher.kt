package dev.helight.krescent.source

import dev.helight.krescent.event.EventMessage
import kotlinx.coroutines.channels.Channel

/**
 * A decorator implementation of the EventPublisher interface that notifies a given channel
 * each time an event is published. This can be useful for triggering side operations or signaling
 * other components when event publishing occurs.
 *
 * @property original The original EventPublisher that handles the actual event publishing.
 * @property channel A channel that receives a signal after an event is successfully published.
 */
class NotifyingEventPublisher(
    val original: EventPublisher,
    val channel: Channel<Unit>,
) : EventPublisher {
    override suspend fun publish(event: EventMessage) {
        original.publish(event)
        channel.trySend(Unit)
    }

    override suspend fun publishAll(events: List<EventMessage>) {
        original.publishAll(events)
        channel.trySend(Unit)
    }

    companion object {
        fun EventPublisher.channelNotifying(channel: Channel<Unit>): EventPublisher =
            NotifyingEventPublisher(this, channel)
    }
}