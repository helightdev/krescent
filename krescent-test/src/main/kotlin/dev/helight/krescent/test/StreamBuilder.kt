package dev.helight.krescent.test

import dev.helight.krescent.event.Event
import dev.helight.krescent.event.EventCatalog
import dev.helight.krescent.event.EventMessage
import dev.helight.krescent.source.StoredEventSource
import dev.helight.krescent.source.impl.InMemoryEventStore
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import kotlin.time.Duration

suspend fun buildStream(catalog: EventCatalog, block: StreamBuilder.() -> Unit): InMemoryEventStore {
    val builder = StreamBuilder(catalog)
    builder.block()
    return builder.build()
}

class StreamBuilder(
    val catalog: EventCatalog,
) {
    private var list: MutableList<EventMessage> = mutableListOf()
    private var time = 0L

    fun <T : Event> event(event: T): StreamBuilder {
        val message = catalog.create(event).copy(timestamp = Instant.fromEpochMilliseconds(time))
        list.add(message)
        return this
    }

    operator fun Event.unaryPlus() {
        val messsage = catalog.create(this).copy(timestamp = Instant.fromEpochMilliseconds(time))
        list.add(messsage)
    }

    fun at(time: Instant): StreamBuilder {
        this.time = time.toEpochMilliseconds()
        return this
    }

    fun sleep(millis: Long): StreamBuilder {
        time += millis
        return this
    }

    fun sleep(duration: Duration): StreamBuilder {
        time += duration.inWholeMilliseconds
        return this
    }

    fun include(vararg events: EventMessage): StreamBuilder {
        list.addAll(events)
        return this
    }

    suspend fun include(source: StoredEventSource): StreamBuilder {
        source.fetchEventsAfter().map { it.first }.collect {
            list.add(it.copy(timestamp = Instant.fromEpochMilliseconds(time)))
        }
        return this
    }

    suspend fun combine(source: StoredEventSource): StreamBuilder {
        source.fetchEventsAfter().map { it.first }.collect {
            list.add(it)
        }
        return this
    }

    internal suspend fun build(): InMemoryEventStore = InMemoryEventStore().also {
        list.sortBy { message -> message.timestamp }
        it.publishAll(list)
    }
}
