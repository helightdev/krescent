package dev.helight.krescent.source.impl

import dev.helight.krescent.event.EventMessage
import dev.helight.krescent.event.logging.ConsoleLoggingEventStreamProcessor.Companion.consoleLog
import dev.helight.krescent.source.StreamingToken
import dev.helight.krescent.timeoutBufferInMemory
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

class MergingStreamingEventSourceTest {
    @Test
    fun `Simple already ordered event join`() = runBlocking {
        val a = InMemoryEventStore(
            EventMessage(type = "a", timestamp = Instant.fromEpochSeconds(0L), payload = JsonPrimitive(1)),
            EventMessage(type = "a", timestamp = Instant.fromEpochSeconds(1L), payload = JsonPrimitive(2)),
            EventMessage(type = "a", timestamp = Instant.fromEpochSeconds(2L), payload = JsonPrimitive(3))
        )
        val b = InMemoryEventStore(
            EventMessage(type = "b", timestamp = Instant.fromEpochSeconds(3L), payload = JsonPrimitive(4)),
            EventMessage(type = "b", timestamp = Instant.fromEpochSeconds(4L), payload = JsonPrimitive(5)),
            EventMessage(type = "b", timestamp = Instant.fromEpochSeconds(5L), payload = JsonPrimitive(6))
        )
        val merged = MergingStreamingEventSource(
            mapOf(
                "a" to a,
                "b" to b
            ), minAge = 0L
        )
        assertPayloadOrdered(merged.fetchEventsAfter().toList(), 6)
        assertPayloadOrdered(merged.streamEvents().timeoutBufferInMemory(this, 100), 6)
    }

    @Test
    fun `More complex stream with resume`() = runBlocking {
        val a = InMemoryEventStore(
            EventMessage(type = "a", timestamp = Instant.fromEpochSeconds(0L), payload = JsonPrimitive(1)),
            EventMessage(type = "a", timestamp = Instant.fromEpochSeconds(100L), payload = JsonPrimitive(5)),
            EventMessage(type = "a", timestamp = Instant.fromEpochSeconds(200L), payload = JsonPrimitive(6))
        )
        val b = InMemoryEventStore(
            EventMessage(type = "b", timestamp = Instant.fromEpochSeconds(3L), payload = JsonPrimitive(2)),
            EventMessage(type = "b", timestamp = Instant.fromEpochSeconds(4L), payload = JsonPrimitive(3)),
            EventMessage(type = "b", timestamp = Instant.fromEpochSeconds(5L), payload = JsonPrimitive(4))
        )
        val merged = MergingStreamingEventSource(
            mapOf(
                "a" to a,
                "b" to b
            ), minAge = 0L
        )
        assertPayloadOrdered(merged.fetchEventsAfter().toList(), 6)
        assertPayloadOrdered(merged.streamEvents().timeoutBufferInMemory(this, 100), 6)

        // Check resume
        val resumedBuffer = mutableListOf<Pair<EventMessage, StreamingToken<*>>>()
        var lastToken: StreamingToken<*>? = null
        merged.fetchEventsAfter(null, 3).collect {
            resumedBuffer.add(it)
            lastToken = it.second
        }
        assertEquals(3, resumedBuffer.size)
        merged.fetchEventsAfter(lastToken, 3).collect {
            resumedBuffer.add(it)
            lastToken = it.second
        }
        assertEquals(6, resumedBuffer.size)
        assertPayloadOrdered(resumedBuffer, 6)
    }

    @Test
    fun `Test deadline cuts off too recent events`() = runBlocking {
        val now = Clock.System.now()
        val a = InMemoryEventStore(
            EventMessage(type = "a", timestamp = now - (1000 * 8).milliseconds, payload = JsonPrimitive(1)),
            EventMessage(type = "a", timestamp = now - (1000 * 7).milliseconds, payload = JsonPrimitive(2)),
            EventMessage(type = "a", timestamp = now - (1000 * 1).milliseconds, payload = JsonPrimitive(3))
        )
        val merged = MergingStreamingEventSource(
            mapOf(
                "a" to a
            ), minAge = 1000 * 5, streamPrefetch = false
        ) // 5 seconds
        val events = merged.fetchEventsAfter().toList()
        assertEquals(2, events.size) // Only the first two events should be returned
        val streamedEvents = merged.streamEvents().timeoutBufferInMemory(this, 100).toList()
        assertEquals(2, streamedEvents.size) // Only the first two events should be streamed

    }

    @Test
    fun `Test batching works as expected`() = runBlocking {
        val a = InMemoryEventStore(
            EventMessage(type = "a", timestamp = Instant.fromEpochSeconds(0L), payload = JsonPrimitive(1)),
            EventMessage(type = "a", timestamp = Instant.fromEpochSeconds(1L), payload = JsonPrimitive(2)),
            EventMessage(type = "a", timestamp = Instant.fromEpochSeconds(2L), payload = JsonPrimitive(3))
        )
        val b = InMemoryEventStore(
            EventMessage(type = "b", timestamp = Instant.fromEpochSeconds(3L), payload = JsonPrimitive(4)),
            EventMessage(type = "b", timestamp = Instant.fromEpochSeconds(4L), payload = JsonPrimitive(5)),
            EventMessage(type = "b", timestamp = Instant.fromEpochSeconds(5L), payload = JsonPrimitive(6))
        )
        val merged = MergingStreamingEventSource(
            mapOf(
                "a" to a,
                "b" to b
            ), batchSize = 2, minAge = 0L
        )
        assertPayloadOrdered(merged.fetchEventsAfter().toList(), 6)
        assertPayloadOrdered(merged.streamEvents().timeoutBufferInMemory(this, 100), 6)
    }

    @Test
    fun `Check streaming works and also respects the deadline`() = runBlocking {
        val now = Clock.System.now()
        val a = InMemoryEventStore(
            EventMessage(type = "a", timestamp = now - (1000 * 8).milliseconds, payload = JsonPrimitive(1)),
            EventMessage(type = "a", timestamp = now - (1000 * 7).milliseconds, payload = JsonPrimitive(2)),
            EventMessage(type = "a", timestamp = now, payload = JsonPrimitive(3))
        )

        val buffer = mutableListOf<Pair<EventMessage, StreamingToken<*>>>()
        val merged = MergingStreamingEventSource(
            mapOf(
                "a" to a
            ), minAge = 500, pollingInterval = 100
        ) // 0.5 seconds

        val job = launch {
            merged.streamEvents().collect { event ->
                buffer.add(event)
            }
        }
        a.publish(EventMessage(type = "a", payload = JsonPrimitive(4)))
        delay(100)
        a.publish(EventMessage(type = "a", payload = JsonPrimitive(5)))
        delay(600)
        a.publish(EventMessage(type = "a", payload = JsonPrimitive(6)))
        job.cancel()

        buffer.map { it.first }.consoleLog()
        assertEquals(5, buffer.size)
        assertPayloadOrdered(buffer, 5)
    }

    @Suppress("SameParameterValue")
    private fun assertPayloadOrdered(list: List<Pair<EventMessage, StreamingToken<*>>>, last: Int) {
        var expected = 1
        list.forEach {
            val payload = it.first.payload.jsonPrimitive.int
            assert(payload == expected) { "Expected $expected but got $payload for ${it.second}" }
            expected++
        }
        assertEquals(last, expected - 1)
    }

}