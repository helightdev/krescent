package dev.helight.krescent.event.logging

import dev.helight.krescent.event.Event
import dev.helight.krescent.event.EventMessage
import dev.helight.krescent.source.StreamingToken

interface EventStreamLogFormatter {

    fun formatEvent(event: Event, index: Long): String
    fun formatMessage(message: EventMessage, position: StreamingToken<*>, index: Long): String

}