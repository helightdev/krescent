package dev.helight.krescent.test

import dev.helight.krescent.bufferInMemory
import dev.helight.krescent.event.EventMessage
import dev.helight.krescent.event.logging.ConsoleLoggingEventStreamProcessor.Companion.consoleLog
import dev.helight.krescent.source.EventPublisher
import dev.helight.krescent.source.StreamingEventSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

interface StreamingEventSourceContract {

    fun execWithStreamingSource(block: suspend CoroutineScope.(StreamingEventSource, EventPublisher) -> Unit)

    @Test
    fun `Fetch Empty Stream`() = execWithStreamingSource { eventSource, publisher ->
        val fetched = eventSource.fetchEventsAfter().toList()
        assertTrue(fetched.isEmpty())

        var hasResolved = false
        val job = launch {
            eventSource.streamEvents().collect {
                hasResolved = true
            }
        }
        delay(100)
        job.cancel()
        assertFalse(hasResolved)
    }

    @Test
    fun `Publish Events`() = execWithStreamingSource { source, publisher ->
        publisher.publish(EventMessage(type = "my-type", payload = buildJsonObject {
            put("number", 1)
        }))
        publisher.publish(EventMessage(type = "my-type", payload = buildJsonObject {
            put("number", 2)
        }))
        publisher.publish(EventMessage(type = "my-type", payload = buildJsonObject {
            put("number", 3)
        }))

        val returned = source.fetchEventsAfter(source.getHeadToken()).toList()
        assertEquals(3, returned.size)
        assertEquals(1, returned[0].first.payload.jsonObject["number"]?.jsonPrimitive!!.int)
        assertEquals(3, returned[2].first.payload.jsonObject["number"]?.jsonPrimitive!!.int)
    }


    @Test
    fun `Write and Resolve Events`() = execWithStreamingSource { eventSource, publisher ->
        publisher.publish(EventMessage(type = "my-type", payload = buildJsonObject {
            put("number", 1)
        }))
        publisher.publish(EventMessage(type = "my-type", payload = buildJsonObject {
            put("number", 2)
        }))
        publisher.publish(EventMessage(type = "my-type", payload = buildJsonObject {
            put("number", 3)
        }))

        val events = eventSource.fetchEventsAfter(eventSource.getHeadToken()).toList()
        events.map { it.first }.consoleLog()
        assertEquals(3, events.size)
    }

    @Test
    fun `Write and listen to Events`() = execWithStreamingSource { eventSource, publisher ->
        publisher.publish(EventMessage(type = "my-type", payload = buildJsonObject {
            put("number", 1)
        }))
        publisher.publish(EventMessage(type = "my-type", payload = buildJsonObject {
            put("number", 2)
        }))
        val buffer = eventSource.streamEvents().bufferInMemory(this)
        delay(100)
        publisher.publish(EventMessage(type = "my-type", payload = buildJsonObject {
            put("number", 3)
        }))
        delay(100)
        publisher.publish(EventMessage(type = "my-type", payload = buildJsonObject {
            put("number", 4)
        }))
        delay(500)
        val timeline = buffer.stop()
        timeline.map { it.first }.consoleLog()
        assertEquals(4, timeline.size)
        assertEquals(1, timeline[0].first.payload.jsonObject["number"]?.jsonPrimitive?.int)
        assertEquals(2, timeline[1].first.payload.jsonObject["number"]?.jsonPrimitive?.int)
        assertEquals(3, timeline[2].first.payload.jsonObject["number"]?.jsonPrimitive?.int)
        assertEquals(4, timeline[3].first.payload.jsonObject["number"]?.jsonPrimitive?.int)
    }

    @Test
    fun `Listen at the tail after events have already been inserted`() =
        execWithStreamingSource { eventSource, publisher ->
            publisher.publish(EventMessage(type = "my-type", payload = buildJsonObject {
                put("number", 1)
            }))
            publisher.publish(EventMessage(type = "my-type", payload = buildJsonObject {
                put("number", 2)
            }))
            publisher.publish(EventMessage(type = "my-type", payload = buildJsonObject {
                put("number", 3)
            }))
            val buffer = eventSource.streamEvents(eventSource.getTailToken()).bufferInMemory(this)
            delay(100)
            publisher.publish(EventMessage(type = "my-type", payload = buildJsonObject {
                put("number", 4)
            }))
            delay(500)
            val timeline = buffer.stop()
            timeline.map { it.first }.consoleLog()
            assertEquals(1, timeline.size)
            assertEquals(4, timeline[0].first.payload.jsonObject["number"]?.jsonPrimitive?.int)
        }

    @Test
    fun `Read after a specific revision`() = execWithStreamingSource { eventSource, publisher ->
        publisher.publish(EventMessage(type = "my-type", payload = buildJsonObject {
            put("number", 1)
        }))
        publisher.publish(EventMessage(type = "my-type", payload = buildJsonObject {
            put("number", 2)
        }))
        publisher.publish(EventMessage(type = "my-type", payload = buildJsonObject {
            put("number", 3)
        }))
        publisher.publish(EventMessage(type = "my-type", payload = buildJsonObject {
            put("number", 4)
        }))
        val first = eventSource.fetchEventsAfter(eventSource.getHeadToken(), 2).toList()
        val buffer = eventSource.fetchEventsAfter(first.last().component2()).toList()
        assertEquals(2, buffer.size)
        assertEquals(3, buffer[0].first.payload.jsonObject["number"]?.jsonPrimitive?.int)
        assertEquals(4, buffer[1].first.payload.jsonObject["number"]?.jsonPrimitive?.int)
    }


}