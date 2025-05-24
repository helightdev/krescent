package dev.helight.krescent.event

import dev.helight.krescent.StreamingToken
import java.time.Instant

abstract class Event {
    lateinit var metadata: EventMetadata
}

data class EventMetadata(
    val id: String,
    val type: String,
    val timestamp: Instant,
    val position: StreamingToken<*>?
)

abstract class VirtualEvent : Event()