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

class ConsoleLoggingEventStreamProcessor(
    val formatter: EventStreamLogFormatter = ColoredEventStreamLogFormatter(),
) : ModelExtension<Unit>, EventStreamProcessor, EventMessageStreamProcessor {

    private var counter: Long = 0

    override suspend fun process(event: Event) {
        val index = counter++
        val formattedMessage = formatter.formatEvent(event, index)
        println(formattedMessage)
    }

    override suspend fun process(
        message: EventMessage,
        position: StreamingToken<*>,
    ) {
        val index = counter++
        val formattedMessage = formatter.formatMessage(message, position, index)
        println(formattedMessage)
    }

    override suspend fun forwardSystemEvent(event: Event) {
        val index = counter++
        val formattedMessage = formatter.formatEvent(event, index)
        println(formattedMessage)
    }

    override fun unpack() = Unit

    companion object {

        fun <M : EventModelBase> M.consoleLogging(
            formatter: EventStreamLogFormatter = ColoredEventStreamLogFormatter(),
        ): M {
            registerExtension(ConsoleLoggingEventStreamProcessor(formatter))
            return this
        }

        fun ExtensionAwareBuilder.useConsoleLogging(
            formatter: EventStreamLogFormatter = ColoredEventStreamLogFormatter(),
        ) {
            registerExtension(ConsoleLoggingEventStreamProcessor(formatter))
        }

        suspend fun Iterable<EventMessage>.consoleLog(
            formatter: EventStreamLogFormatter = ColoredEventStreamLogFormatter(),
        ) {
            ReplayingEventSourceConsumer(
                InMemoryEventStore(this.toMutableList()),
                ConsoleLoggingEventStreamProcessor(formatter)
            ).catchup()
        }
    }
}

