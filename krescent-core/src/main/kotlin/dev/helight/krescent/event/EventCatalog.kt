package dev.helight.krescent.event

import dev.helight.krescent.EventMessage
import dev.helight.krescent.StreamingToken
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer
import kotlin.reflect.KClass

fun buildEventCatalog(block: EventCatalogBuilder.() -> Unit): EventCatalog {
    val builder = EventCatalogBuilder()
    builder.block()
    return builder.build()
}

class EventCatalogBuilder {

    val registrations: MutableList<EventRegistration<*>> = mutableListOf()

    inline fun <reified T: Event> event(name: String) {
        val registration = EventRegistration(
            type = T::class,
            eventName = name,
            serializer = serializer<T>(),
        )
        registrations.add(registration)
    }

    fun build(): EventCatalog {
        val events = registrations.associateBy { it.eventName }
        val eventTypes = registrations.associateBy { it.type }
        return EventCatalog(
            events = events,
            eventTypes = eventTypes
        )
    }
}

class EventCatalog(
    val events: Map<String, EventRegistration<*>>,
    val eventTypes: Map<KClass<out Event>, EventRegistration<*>>,
) {
    fun decode(message: EventMessage, position: StreamingToken<*>?): Event? {
        val type = message.type
        val eventRegistration = events[type] ?: return null
        val evt = Json.Default.decodeFromJsonElement(eventRegistration.serializer, message.payload)
        evt.metadata = EventMetadata(
            id = message.id,
            type = type,
            timestamp = message.timestamp,
            position = position
        )
        return evt
    }

    fun encode(event: Event): EventMessage {
        val eventRegistration = eventTypes[event::class] ?: error("Event type not registered")
        val payload = eventRegistration.unsafeEncode(event)
        return EventMessage(
            id = event.metadata.id,
            timestamp = event.metadata.timestamp,
            type = eventRegistration.eventName,
            payload = payload
        )
    }
}

data class EventRegistration<T: Event>(
    val type: KClass<T>,
    val eventName: String,
    val serializer: KSerializer<T>,
) {
    @Suppress("UNCHECKED_CAST")
    fun unsafeEncode(event: Event): JsonElement {
        return Json.encodeToJsonElement(serializer, event as T)
    }
}