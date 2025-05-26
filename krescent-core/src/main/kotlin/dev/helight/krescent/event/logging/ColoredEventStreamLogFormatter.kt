package dev.helight.krescent.event.logging

import dev.helight.krescent.event.*
import dev.helight.krescent.source.StreamingToken
import kotlinx.serialization.json.jsonObject

class ColoredEventStreamLogFormatter(
    val indexWidth: Int = 5,
    val positionWidth: Int = 10,
    val eventNameWidth: Int = 32,
    val payloadWidth: Int = 32,
) : EventStreamLogFormatter {

    companion object {
        val RESET = "\u001B[0m"
        val GREY = "\u001B[37m"
        val DARK_GREY = "\u001B[90m"
        val PHYSICAL = "\u001B[33mPHYSICAL\u001B[0m"
        val SYSTEM = "\u001B[34m  SYSTEM\u001B[0m"
        val VIRTUAL = "\u001B[35m VIRTUAL\u001B[0m"
        val UNKNOWN = "\u001B[31m UNKNOWN\u001B[0m"
        val EMITTED = "\u001B[32m EMITTED\u001B[0m"
    }

    override fun formatEvent(event: Event, index: Long): String {
        val tag = when {
            event is SystemEmittedEvent -> EMITTED
            event is SystemEvent -> SYSTEM
            event is VirtualEvent -> VIRTUAL
            event.metadata != null -> PHYSICAL
            else -> UNKNOWN
        }
        val indexStr = index.toString().padStart(indexWidth, '0')
        val positionIndex = when (event.metadata?.position) {
            null -> "$GREY${"-".repeat(positionWidth)}$RESET"
            else -> event.metadata!!.position!!.serialize().padEnd(positionWidth)
        }
        val eventName = when (event) {
            is SystemEmittedEvent -> event.event::class.simpleName!!.take(eventNameWidth).padEnd(eventNameWidth)
            else -> event::class.simpleName!!.take(eventNameWidth).padEnd(eventNameWidth)
        }
        return buildString {
            append("$DARK_GREY#")
            append(indexStr)
            append("$RESET ")
            append(tag)
            append(" ")
            append(positionIndex)
            append(" [")
            append(eventName)
            append("] $GREY")

            append(event.toString().padEnd(payloadWidth))
            if (event.metadata != null) {
                append(" | ")
                append(event.metadata!!.id)
            }
        }
    }

    override fun formatMessage(message: EventMessage, position: StreamingToken<*>, index: Long): String {
        val indexStr = index.toString().padStart(indexWidth, '0')
        val positionIndex = position.serialize().padEnd(positionWidth)
        val eventName = message.type.take(eventNameWidth - 1).padEnd(eventNameWidth - 1)
        val payload = message.payload.jsonObject.toString().padEnd(payloadWidth)
        return "$DARK_GREY#$indexStr$RESET $PHYSICAL $positionIndex [$${eventName}] $GREY$payload | ${message.id}"
    }
}