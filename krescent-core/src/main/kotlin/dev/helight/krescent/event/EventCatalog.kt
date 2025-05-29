package dev.helight.krescent.event

import dev.helight.krescent.source.StreamingToken
import dev.helight.krescent.source.impl.InMemoryEventStore
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer
import kotlin.reflect.KClass

/**
 * Builds an [EventCatalog] with the given revision.
 *
 * The revision is used to track breaking changes in the event catalog, changing the revision will invalidate
 * checkpoints of all dependent models.
 */
fun buildEventCatalog(revision: Int, block: EventCatalogBuilder.() -> Unit): EventCatalog {
    val builder = EventCatalogBuilder(revision)
    builder.block()
    return builder.build()
}

class EventCatalogBuilder(
    val revision: Int
) {

    val registrations: MutableList<EventCatalog.EventRegistration<*>> = mutableListOf()

    /**
     * Registers an event of type [T] with the given [name].
     *
     * @param T The type of the event. Must be a subclass of [Event] and serializable using kotlinx.serialization.
     */
    inline fun <reified T : Event> event(name: String) {
        val registration = EventCatalog.EventRegistration(
            type = T::class,
            eventName = name,
            serializer = serializer<T>(),
        )
        registrations.add(registration)
    }

    internal fun build(): EventCatalog {
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

    /**
     * Decodes an [EventMessage] at the given [position] into an [Event].
     */
    fun decode(message: EventMessage, position: StreamingToken<*>): Event? {
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

    /**
     * Encodes the given event back into an [EventMessage].
     *
     * This requires the event to already have metadata set, which is typically the case for physical events.
     */
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

    /**
     * Creates an [EventMessage] from the given [event].
     */
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

    /**
     * Creates an [InMemoryEventStore] with the given events.
     *
     * @param events The events to store in the in-memory event store.
     */
    fun memoryStore(vararg events: Event): InMemoryEventStore {
        return InMemoryEventStore(events.map {
            create(it)
        }.toMutableList())
    }

    /**
     * Creates an [InMemoryEventStore] with the given collection of events.
     *
     * @param events The collection of events to store in the in-memory event store.
     */
    fun memoryStore(events: Collection<Event>): InMemoryEventStore {
        return InMemoryEventStore(events.map {
            create(it)
        }.toMutableList())
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