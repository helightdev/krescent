package dev.helight.krescent.event.logging

import dev.helight.krescent.event.*
import dev.helight.krescent.source.StreamingToken
import kotlinx.serialization.json.jsonObject

class DefaultEventStreamLogFormatter(
    val indexWidth: Int = 5,
    val positionWidth: Int = 10,
    val eventNameWidth: Int = 32,
    val payloadWidth: Int = 32,
) : EventStreamLogFormatter {

    override fun formatEvent(event: Event, index: Long): String {
        val tag = when {
            event is SystemEmittedEvent -> " EMITTED"
            event is SystemEvent -> "  SYSTEM"
            event is VirtualEvent -> " VIRTUAL"
            event.metadata != null -> "PHYSICAL"
            else -> " UNKNOWN"
        }
        val indexStr = index.toString().padStart(indexWidth, '0')
        val positionIndex = when (event.metadata?.position) {
            null -> "-".repeat(positionWidth)
            else -> event.metadata!!.position!!.serialize().padEnd(positionWidth)
        }
        val eventName = when (event) {
            is SystemEmittedEvent -> event.event::class.simpleName!!.take(eventNameWidth).padEnd(eventNameWidth)
            else -> event::class.simpleName!!.take(eventNameWidth).padEnd(eventNameWidth)
        }
        return buildString {
            append("#")
            append(indexStr)
            append(" ")
            append(tag)
            append(" ")
            append(positionIndex)
            append(" [")
            append(eventName)
            append("] ")

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
        return "#$indexStr PHYSICAL $positionIndex [$${eventName}] $payload | ${message.id}"
    }
}