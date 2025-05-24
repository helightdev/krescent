package dev.helight.krescent.event.processor

import dev.helight.krescent.HandlerChainParticipant
import dev.helight.krescent.event.*
import dev.helight.krescent.source.StreamingToken
import java.util.function.Consumer

class TransformingModelEventProcessor<T : StreamingToken<T>>(
    catalog: EventCatalog,
    virtualEvents: List<Pair<String, VirtualEventIngest>>,
    modelHandler: EventStreamProcessor,
) : EventMessageStreamProcessor {

    val virtualStreams = VirtualEventEventStreamProcessor(virtualEvents, modelHandler)
    val catalogProcessor = CatalogProcessor(catalog, virtualStreams)

    override suspend fun process(
        message: EventMessage,
        position: StreamingToken<*>,
    ) = catalogProcessor.process(message, position)

    override suspend fun forwardSystemEvent(event: Event) {
        catalogProcessor.forwardSystemEvent(event)
    }

    override fun accept(visitor: Consumer<HandlerChainParticipant>) {
        super.accept(visitor)
        catalogProcessor.accept(visitor)
    }
}