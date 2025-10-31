package dev.helight.krescent.test

import dev.helight.krescent.event.Event
import dev.helight.krescent.event.EventCatalog
import dev.helight.krescent.event.SystemEvent
import dev.helight.krescent.source.StoredEventSource
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
import kotlin.test.assertFails
import kotlin.test.asserter

internal suspend inline fun assertStreamFails(
    catalog: EventCatalog,
    source: StoredEventSource,
    crossinline block: suspend StreamMatcher.() -> Unit,
) = assertFails {
    assertStream(catalog, source, includeSystemEvents = false, block = block)
}

suspend inline fun assertStream(
    catalog: EventCatalog,
    source: StoredEventSource,
    includeSystemEvents: Boolean = false,
    crossinline block: suspend StreamMatcher.() -> Unit,
) {
    val list = source.fetchEventsAfter()
        .mapNotNull { catalog.decode(it.first, it.second) }
        .filter { includeSystemEvents || it !is SystemEvent }
        .toList()

    val matcher = StreamMatcher(list, catalog)
    matcher.block()
}

class StreamMatcher(
    val events: List<Event>,
    private val catalog: EventCatalog,
) {
    @PublishedApi
    internal var cursor = 0

    @PublishedApi
    internal val size: Int
        get() = events.size

    @PublishedApi
    internal val available: Int
        get() = events.size - cursor

    fun countExact(count: Int): List<Event> {
        asserter.assertTrue({
            "Expected $count events, but got ${events.size}"
        }, count == events.size)
        return events.subList(cursor, cursor + count)
    }

    fun countAtLeast(count: Int): List<Event> {
        asserter.assertTrue({
            "Expected at least $count more event(s), but got $available"
        }, available >= count)
        return events.subList(cursor, cursor + count)
    }

    fun countAvailableExact(count: Int): List<Event> {
        asserter.assertTrue({
            "Expected $count available events, but got $available"
        }, available == count)
        return events.subList(cursor, cursor + count)
    }

    fun countAvailableAtLeast(count: Int): List<Event> {
        asserter.assertTrue({
            "Expected at least $count available events, but got $available"
        }, available >= count)
        return events.subList(cursor, cursor + count)
    }

    fun peek(): Event? {
        val hasNext = available >= 1
        if (!hasNext) return null
        return events[cursor]
    }

    fun nextEvent(): Event {
        countAtLeast(1)
        val event = events[cursor]
        cursor++
        return event
    }

    @JvmName("nextEventTyped")
    inline fun <reified T : Event> nextEvent(): T {
        val evt = nextEvent()
        asserter.assertTrue(
            { "Expected an event of type ${T::class.java.simpleName} but got ${evt::class.java.simpleName}" },
            evt is T
        )
        return evt as T
    }

    @JvmName("nextEventTyped")
    inline fun <reified T : Event> nextEvent(message: String? = null, crossinline matcher: (T) -> Boolean): T {
        val evt = nextEvent<T>()
        asserter.assertTrue(
            { message ?: "Event of type ${T::class.java.simpleName} did not match the expected criteria." },
            matcher(evt)
        )
        return evt
    }

    inline fun <reified T : Event> ifNextEvent(message: String? = null, crossinline matcher: (T) -> Boolean): T? {
        if (available == 0) return null
        val evt = nextEvent<T>()
        asserter.assertTrue(
            { message ?: "Event of type ${T::class.java.simpleName} did not match the expected criteria." },
            matcher(evt)
        )
        return evt
    }

    fun hasEvent() {
        asserter.assertTrue(
            { "Expected at least one event, but none was found." },
            events.isNotEmpty()
        )
    }

    @JvmName("hasEventTyped")
    inline fun <reified T : Event> hasEvent() {
        val found = events.any { it is T }
        asserter.assertTrue(
            { "Expected at least one event of type ${T::class.java.simpleName}, but none was found." },
            found
        )
    }

    @JvmName("hasEventTyped")
    inline fun <reified T : Event> hasEvent(message: String? = null, crossinline matcher: (T) -> Boolean) {
        hasEvent<T>()
        val found = events.any { it is T && matcher(it) }
        asserter.assertTrue(
            { message ?: "No event of type ${T::class.java.simpleName} matched the expected criteria." },
            found
        )
    }

    fun hasNoEvent() {
        asserter.assertTrue(
            { "Expected no events, but some were found." },
            events.isEmpty()
        )
    }

    @JvmName("hasNoEventTyped")
    inline fun <reified T : Event> hasNoEvent() {
        val found = events.any { it is T }
        asserter.assertTrue(
            { "Expected no events of type ${T::class.java.simpleName}, but some were found." },
            !found
        )
    }

    @JvmName("hasNoEventTyped")
    inline fun <reified T : Event> hasNoEvent(message: String? = null, crossinline matcher: (T) -> Boolean) {
        val found = events.any { it is T && matcher(it) }
        asserter.assertTrue(
            { message ?: "Some events of type ${T::class.java.simpleName} matched the unexpected criteria." },
            !found
        )
    }

    fun isTail() {
        asserter.assertTrue(
            { "Expected to be at the end of the stream, but $available event(s) are still available." },
            available == 0
        )
    }
}