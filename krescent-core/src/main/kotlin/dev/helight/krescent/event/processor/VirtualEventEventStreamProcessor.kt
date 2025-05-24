package dev.helight.krescent.event.processor

import dev.helight.krescent.HandlerChainParticipant
import dev.helight.krescent.event.Event
import dev.helight.krescent.event.EventStreamProcessor
import dev.helight.krescent.event.VirtualEventIngest
import java.util.function.Consumer

class VirtualEventEventStreamProcessor(
    val virtualStreams: List<Pair<String, VirtualEventIngest>>,
    val target: EventStreamProcessor,
) : EventStreamProcessor {

    override suspend fun process(event: Event) {
        target.process(event)
        for ((_, transformer) in virtualStreams) {
            transformer.ingest(event).collect {
                target.process(it)
            }
        }
    }

    override fun accept(visitor: Consumer<HandlerChainParticipant>) {
        super.accept(visitor)
        for ((_, transformer) in virtualStreams) {
            if (transformer is HandlerChainParticipant) {
                transformer.accept(visitor)
            }
        }
        target.accept(visitor)
    }
}