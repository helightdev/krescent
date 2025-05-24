package dev.helight.krescent.event

import dev.helight.krescent.EventMessage
import dev.helight.krescent.EventMessageStreamProcessor
import dev.helight.krescent.EventStreamProcessor
import dev.helight.krescent.HandlerChainParticipant
import dev.helight.krescent.StreamingToken
import java.util.function.Consumer

class CatalogProcessor(
    val catalog: EventCatalog,
    val consumer: EventStreamProcessor,
) : EventMessageStreamProcessor {

    override suspend fun process(
        message: EventMessage,
        position: StreamingToken<*>,
    ) {
        val event = catalog.decode(message, position)
        if (event == null) {
            println("Failed to decode event: ${message.type}")
            return
        }
        consumer.process(event)
    }

    override fun accept(visitor: Consumer<HandlerChainParticipant>) {
        super.accept(visitor)
        consumer.accept(visitor)
    }
}