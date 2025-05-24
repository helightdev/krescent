package dev.helight.krescent

import dev.helight.krescent.event.EventMessage
import dev.helight.krescent.source.StreamingToken
import dev.helight.krescent.source.impl.InMemoryEventStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.*

class InMemoryEventStoreTest {

    private lateinit var eventStore: InMemoryEventStore

    @BeforeEach
    fun setUp() {
        eventStore = InMemoryEventStore()
    }

    @Test
    fun `test token serialization and deserialization`() = runBlocking {
        val token = InMemoryEventStore.StreamingToken(42)
        assertEquals("42", token.serialize())

        val deserializedToken = eventStore.deserializeToken("42")
        assertEquals(42, deserializedToken.index)

        // Test invalid token
        val invalidToken = eventStore.deserializeToken("not-a-number")
        assertEquals(-1, invalidToken.index)
    }

    @Test
    fun `test getHeadToken returns token with index -1`() = runBlocking {
        val headToken = eventStore.getHeadToken()
        assertEquals(-1, headToken.index)
    }

    @Test
    fun `test getTailToken returns token with index -1 when store is empty`() = runBlocking {
        val tailToken = eventStore.getTailToken()
        assertEquals(-1, tailToken.index)
    }

    @Test
    fun `test getTailToken returns correct index after publishing events`() = runBlocking {
        // Publish 3 events
        repeat(3) {
            eventStore.publish(createTestEvent())
        }

        val tailToken = eventStore.getTailToken()
        assertEquals(2, tailToken.index)
    }

    @Test
    fun `test getTokenAtTime returns correct token`() = runBlocking {
        val now = Instant.now()
        val event1 = createTestEvent(timestamp = now.minusSeconds(10))
        val event2 = createTestEvent(timestamp = now)
        val event3 = createTestEvent(timestamp = now.plusSeconds(10))

        eventStore.publish(event1)
        eventStore.publish(event2)
        eventStore.publish(event3)

        // Test timestamp before all events
        val tokenBefore = eventStore.getTokenAtTime(now.minusSeconds(20))
        assertEquals(-1, tokenBefore.index)

        // Test timestamp between events
        val tokenMiddle = eventStore.getTokenAtTime(now.minusSeconds(5))
        assertEquals(0, tokenMiddle.index)

        // Test timestamp after all events
        val tokenAfter = eventStore.getTokenAtTime(now.plusSeconds(20))
        assertEquals(2, tokenAfter.index)
    }

    @Test
    fun `test getTokenForEventId returns correct token`() = runBlocking {
        val event1 = createTestEvent(id = "event-1")
        val event2 = createTestEvent(id = "event-2")

        eventStore.publish(event1)
        eventStore.publish(event2)

        // Test existing event ID
        val token1 = eventStore.getTokenForEventId("event-1")
        assertNotNull(token1)
        assertEquals(0, (token1 as InMemoryEventStore.StreamingToken).index)

        val token2 = eventStore.getTokenForEventId("event-2")
        assertNotNull(token2)
        assertEquals(1, (token2 as InMemoryEventStore.StreamingToken).index)

        // Test non-existent event ID
        val tokenNonExistent = eventStore.getTokenForEventId("non-existent")
        assertNull(tokenNonExistent)
    }

    @Test
    fun `test fetchEventsAfter returns correct events`() = runBlocking {
        val event1 = createTestEvent(id = "event-1")
        val event2 = createTestEvent(id = "event-2")
        val event3 = createTestEvent(id = "event-3")

        eventStore.publish(event1)
        eventStore.publish(event2)
        eventStore.publish(event3)

        // Test fetching after head token
        val headToken = eventStore.getHeadToken()
        val eventsAfterHead = eventStore.fetchEventsAfter(headToken).toList()
        assertEquals(3, eventsAfterHead.size)
        assertEquals("event-1", eventsAfterHead[0].first.id)
        assertEquals("event-2", eventsAfterHead[1].first.id)
        assertEquals("event-3", eventsAfterHead[2].first.id)

        // Test fetching after a specific token
        val token1 = eventStore.getTokenForEventId("event-1")!!
        val eventsAfterToken1 = eventStore.fetchEventsAfter(token1).toList()
        assertEquals(2, eventsAfterToken1.size)
        assertEquals("event-2", eventsAfterToken1[0].first.id)
        assertEquals("event-3", eventsAfterToken1[1].first.id)

        // Test fetching with limit
        val eventsWithLimit = eventStore.fetchEventsAfter(headToken, 2).toList()
        assertEquals(2, eventsWithLimit.size)
        assertEquals("event-1", eventsWithLimit[0].first.id)
        assertEquals("event-2", eventsWithLimit[1].first.id)
    }

    @Test
    fun `test streamEvents emits all events and then new events`() = runBlocking {
        val event1 = createTestEvent(id = "event-1")
        val event2 = createTestEvent(id = "event-2")

        // Publish initial events
        eventStore.publish(event1)
        eventStore.publish(event2)

        // Start streaming from the head
        val events = mutableListOf<Pair<EventMessage, StreamingToken<*>>>()
        val flow = eventStore.streamEvents()

        // Collect the first two events
        flow.takeWhile { event ->
            println("Collected event: ${event.first.id}")
            events.add(event)
            if (events.size == 2) {
                eventStore.publish(createTestEvent(id = "event-3"))
                println("Published new event: event-3")
            } else if (events.size == 3) {
                println("Collected 3 events, stopping collection.")
                return@takeWhile false
            }
            true
        }.collect()

        println("Collected all events: ${events.map { it.first.id }}")
        assertEquals(3, events.size)
        assertEquals("event-1", events[0].first.id)
        assertEquals("event-2", events[1].first.id)
        assertEquals("event-3", events[2].first.id)
    }

    @Test
    fun `test delayed publish`() = runBlocking {
        val event1 = createTestEvent(id = "event-1")
        val event2 = createTestEvent(id = "event-2")

        // Publish initial events
        eventStore.publish(event1)
        eventStore.publish(event2)

        launch {
            delay(100)
            eventStore.publish(createTestEvent(id = "event-3"))
        }

        val buffer = eventStore.streamEvents().bufferInMemory(this)
        delay(200)

        val events = buffer.stop()
        println("Collected all events: ${events.map { it.first.id }}")
        assertEquals(3, events.size)
        assertEquals("event-1", events[0].first.id)
        assertEquals("event-2", events[1].first.id)
        assertEquals("event-3", events[2].first.id)
    }

    @Test
    fun `test publish adds event to store`() = runBlocking {
        val event = createTestEvent(id = "test-event")
        eventStore.publish(event)

        // Verify the event was added by fetching it
        val events = eventStore.fetchEventsAfter(eventStore.getHeadToken()).toList()
        assertEquals(1, events.size)
        assertEquals("test-event", events[0].first.id)
    }

    // Helper function to create test events
    private fun createTestEvent(
        id: String = UUID.randomUUID().toString(),
        timestamp: Instant = Instant.now(),
        type: String = "test-event",
        payload: JsonPrimitive = JsonPrimitive("test-payload"),
    ): EventMessage {
        return EventMessage(
            id = id,
            timestamp = timestamp,
            type = type,
            payload = payload
        )
    }
}
