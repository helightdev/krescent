package dev.helight.krescent.event.processor

import dev.helight.krescent.HandlerChainParticipant
import dev.helight.krescent.event.Event
import dev.helight.krescent.event.EventStreamProcessor
import java.util.function.Consumer

class BroadcastEventStreamProcessor(
    private val processors: List<EventStreamProcessor>,
) : EventStreamProcessor {
    override suspend fun process(event: Event) {
        for (processor in processors) {
            processor.process(event)
        }
    }

    override fun accept(visitor: Consumer<HandlerChainParticipant>) {
        super.accept(visitor)
        for (processor in processors) {
            processor.accept(visitor)
        }
    }
}