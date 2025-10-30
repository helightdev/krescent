package dev.helight.krescent.source.impl

import dev.helight.krescent.source.EventPublisher
import dev.helight.krescent.source.NotifyingEventPublisher.Companion.channelNotifying
import dev.helight.krescent.source.PollingStreamingEventSource.Companion.pollingWithNotifications
import dev.helight.krescent.source.StreamingEventSource
import dev.helight.krescent.test.StreamingEventSourceContract
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking

class PollingStreamingEventSourceNotifyingTest : StreamingEventSourceContract {
    override fun execWithStreamingSource(block: suspend CoroutineScope.(StreamingEventSource, EventPublisher) -> Unit) {
        val source = InMemoryEventStore()
        val channel = MutableSharedFlow<Unit>(0)
        val publisher = source.channelNotifying(channel)
        val streaming = source.pollingWithNotifications(channel)
        runBlocking {
            this.block(streaming, publisher)
        }
    }
}