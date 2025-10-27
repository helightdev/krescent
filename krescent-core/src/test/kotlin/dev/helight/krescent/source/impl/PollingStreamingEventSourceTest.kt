package dev.helight.krescent.source.impl

import dev.helight.krescent.source.EventPublisher
import dev.helight.krescent.source.PollingStreamingEventSource.Companion.polling
import dev.helight.krescent.source.StreamingEventSource
import dev.helight.krescent.test.StreamingEventSourceContract
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking

class PollingStreamingEventSourceTest : StreamingEventSourceContract {
    override fun execWithStreamingSource(block: suspend CoroutineScope.(StreamingEventSource, EventPublisher) -> Unit) {
        val source = InMemoryEventStore()
        runBlocking {
            this.block(source.polling(), source)
        }
    }
}
