package dev.helight.krescent.event.logging

import dev.helight.krescent.event.Event
import dev.helight.krescent.event.EventMessage
import dev.helight.krescent.event.EventMessageStreamProcessor
import dev.helight.krescent.event.EventStreamProcessor
import dev.helight.krescent.model.EventModelBase
import dev.helight.krescent.model.ExtensionAwareBuilder
import dev.helight.krescent.model.ModelExtension
import dev.helight.krescent.source.ReplayingEventSourceConsumer
import dev.helight.krescent.source.StreamingToken
import dev.helight.krescent.source.impl.InMemoryEventStore

class BufferLoggingEventStreamProcessor(
    val formatter: EventStreamLogFormatter = DefaultEventStreamLogFormatter(),
    private val buffer: MutableList<String> = mutableListOf(),
) : ModelExtension<List<String>>, EventStreamProcessor, EventMessageStreamProcessor {

    private var counter: Long = 0

    override suspend fun process(event: Event) {
        val index = counter++
        val formattedMessage = formatter.formatEvent(event, index)
        buffer.add(formattedMessage)
    }

    override suspend fun process(
        message: EventMessage,
        position: StreamingToken<*>,
    ) {
        val index = counter++
        val formattedMessage = formatter.formatMessage(message, position, index)
        buffer.add(formattedMessage)
    }

    override suspend fun forwardSystemEvent(event: Event) {
        val index = counter++
        val formattedMessage = formatter.formatEvent(event, index)
        buffer.add(formattedMessage)
    }

    fun getBuffer(): List<String> = buffer

    override fun unpack() = getBuffer()

    companion object {
        fun <M : EventModelBase> M.bufferLogging(
            formatter: EventStreamLogFormatter = DefaultEventStreamLogFormatter(),
        ): M {
            registerExtension(BufferLoggingEventStreamProcessor(formatter))
            return this
        }

        fun ExtensionAwareBuilder.useBufferLogging(
            formatter: EventStreamLogFormatter = DefaultEventStreamLogFormatter(),
            buffer: MutableList<String> = mutableListOf(),
        ) {
            registerExtension(BufferLoggingEventStreamProcessor(formatter, buffer))
        }

        suspend fun Iterable<EventMessage>.bufferLog(
            formatter: EventStreamLogFormatter = DefaultEventStreamLogFormatter(),
            buffer: MutableList<String> = mutableListOf(),
        ) {
            ReplayingEventSourceConsumer(
                InMemoryEventStore(this.toMutableList()),
                BufferLoggingEventStreamProcessor(formatter, buffer)
            ).catchup()
        }
    }
}