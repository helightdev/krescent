package dev.helight.krescent.event

import dev.helight.krescent.source.StreamingToken
import java.time.Instant

abstract class Event {
    var metadata: EventMetadata? = null
}

data class EventMetadata(
    val id: String,
    val type: String,
    val timestamp: Instant,
    val position: StreamingToken<*>?,
)

abstract class VirtualEvent : Event()

class SystemStreamHeadEvent : VirtualEvent()
class SystemStreamRestoredEvent : VirtualEvent()