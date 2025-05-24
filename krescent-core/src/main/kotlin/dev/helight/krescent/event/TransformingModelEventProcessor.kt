package dev.helight.krescent.event

import dev.helight.krescent.EventMessage
import dev.helight.krescent.EventMessageStreamProcessor
import dev.helight.krescent.EventStreamProcessor
import dev.helight.krescent.HandlerChainParticipant
import dev.helight.krescent.StreamingToken
import dev.helight.krescent.VirtualEventStreamTransformer
import java.util.function.Consumer

class TransformingModelEventProcessor<T : StreamingToken<T>>(
    val catalog: EventCatalog,
    val virtualEvents: List<Pair<String, VirtualEventStreamTransformer>>,
    val modelHandler: EventStreamProcessor,
) : EventMessageStreamProcessor {

    val virtualStreams = VirtualEventStreamProcessor(virtualEvents, modelHandler)
    val catalogProcessor = CatalogProcessor(catalog, virtualStreams)

    override suspend fun process(
        message: EventMessage,
        position: StreamingToken<*>,
    ) = catalogProcessor.process(message, position)


    override fun accept(visitor: Consumer<HandlerChainParticipant>) {
        super.accept(visitor)
        catalogProcessor.accept(visitor)
    }
}