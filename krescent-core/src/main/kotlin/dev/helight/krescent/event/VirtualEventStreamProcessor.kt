package dev.helight.krescent.event

import dev.helight.krescent.EventStreamProcessor
import dev.helight.krescent.HandlerChainParticipant
import dev.helight.krescent.VirtualEventStreamTransformer
import java.util.function.Consumer

class VirtualEventStreamProcessor(
    val virtualStreams: List<Pair<String, VirtualEventStreamTransformer>>,
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