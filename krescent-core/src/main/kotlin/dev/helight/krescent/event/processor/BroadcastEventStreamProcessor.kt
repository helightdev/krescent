package dev.helight.krescent.event.processor

import dev.helight.krescent.HandlerChainParticipant
import dev.helight.krescent.event.Event
import dev.helight.krescent.event.EventStreamProcessor
import dev.helight.krescent.event.SystemEvent
import java.util.function.Consumer

class BroadcastEventStreamProcessor(
    private val processors: List<EventStreamProcessor>,
) : EventStreamProcessor {

    override suspend fun process(event: Event) {
        if (event is SystemEvent) {
            val exceptions = mutableListOf<Exception>()
            for (processor in processors) {
                try {
                    processor.process(event)
                } catch (ex: Exception) {
                    exceptions.add(ex)
                }
            }
            if (exceptions.isNotEmpty()) throw SystemEventException(exceptions)
        } else {
            for (processor in processors) {
                processor.process(event)
            }
        }
    }

    override fun accept(visitor: Consumer<HandlerChainParticipant>) {
        super.accept(visitor)
        for (processor in processors) {
            processor.accept(visitor)
        }
    }

    class SystemEventException(
        val exceptions: List<Exception>,
    ) : Exception("Exceptions have occurred while processing a system event: ${exceptions.map { it.message }}")
}