package dev.helight.krescent.event

import dev.helight.krescent.source.StreamingToken
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer
import kotlin.reflect.KClass

fun buildEventCatalog(revision: Int, block: EventCatalogBuilder.() -> Unit): EventCatalog {
    val builder = EventCatalogBuilder(revision)
    builder.block()
    return builder.build()
}

class EventCatalogBuilder(
    val revision: Int
) {

    val registrations: MutableList<EventCatalog.EventRegistration<*>> = mutableListOf()

    inline fun <reified T : Event> event(name: String) {
        val registration = EventCatalog.EventRegistration(
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
            revision = revision,
            events = events,
            eventTypes = eventTypes
        )
    }
}

class EventCatalog(
    val revision: Int,
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

    @Suppress("unused")
    fun encode(event: Event): EventMessage {
        val eventRegistration = eventTypes[event::class] ?: error("Event type not registered")
        val payload = eventRegistration.unsafeEncode(event)
        val metadata = event.metadata
        if (metadata == null) error("Event metadata is null, you can only encode physical events")
        return EventMessage(
            id = metadata.id,
            timestamp = metadata.timestamp,
            type = eventRegistration.eventName,
            payload = payload
        )
    }

    fun create(event: Event): EventMessage {
        val eventRegistration =
            eventTypes[event::class] ?: error("Event type not registered: ${event::class.simpleName}")
        val payload = eventRegistration.unsafeEncode(event)
        val type = eventRegistration.eventName
        return EventMessage(
            type = type,
            payload = payload
        )
    }

    data class EventRegistration<T : Event>(
        val type: KClass<T>,
        val eventName: String,
        val serializer: KSerializer<T>,
    ) {
        @Suppress("UNCHECKED_CAST")
        fun unsafeEncode(event: Event): JsonElement {
            return Json.encodeToJsonElement(serializer, event as T)
        }
    }
}