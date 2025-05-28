package dev.helight.krescent.source.impl

import dev.helight.krescent.bufferInMemory
import dev.helight.krescent.event.EventMessage
import dev.helight.krescent.source.StreamingToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions
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
        val token = InMemoryEventStore.SequenceToken(42)
        Assertions.assertEquals("42", token.serialize())

        val deserializedToken = eventStore.deserializeToken("42")
        Assertions.assertEquals(42, deserializedToken.index)

        // Test invalid token
        val invalidToken = eventStore.deserializeToken("not-a-number")
        Assertions.assertEquals(-1, invalidToken.index)
    }

    @Test
    fun `test getHeadToken returns token with index -1`() = runBlocking {
        val headToken = eventStore.getHeadToken()
        Assertions.assertEquals(-1, headToken.index)
    }

    @Test
    fun `test getTailToken returns token with index -1 when store is empty`() = runBlocking {
        val tailToken = eventStore.getTailToken()
        Assertions.assertEquals(-1, tailToken.index)
    }

    @Test
    fun `test getTailToken returns correct index after publishing events`() = runBlocking {
        // Publish 3 events
        repeat(3) {
            eventStore.publish(createTestEvent())
        }

        val tailToken = eventStore.getTailToken()
        Assertions.assertEquals(2, tailToken.index)
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
        Assertions.assertEquals(-1, tokenBefore.index)

        // Test timestamp between events
        val tokenMiddle = eventStore.getTokenAtTime(now.minusSeconds(5))
        Assertions.assertEquals(0, tokenMiddle.index)

        // Test timestamp after all events
        val tokenAfter = eventStore.getTokenAtTime(now.plusSeconds(20))
        Assertions.assertEquals(2, tokenAfter.index)
    }

    @Test
    fun `test getTokenForEventId returns correct token`() = runBlocking {
        val event1 = createTestEvent(id = "event-1")
        val event2 = createTestEvent(id = "event-2")

        eventStore.publish(event1)
        eventStore.publish(event2)

        // Test existing event ID
        val token1 = eventStore.getTokenForEventId("event-1")
        Assertions.assertNotNull(token1)
        Assertions.assertEquals(0, (token1 as InMemoryEventStore.SequenceToken).index)

        val token2 = eventStore.getTokenForEventId("event-2")
        Assertions.assertNotNull(token2)
        Assertions.assertEquals(1, (token2 as InMemoryEventStore.SequenceToken).index)

        // Test non-existent event ID
        val tokenNonExistent = eventStore.getTokenForEventId("non-existent")
        Assertions.assertNull(tokenNonExistent)
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
        Assertions.assertEquals(3, eventsAfterHead.size)
        Assertions.assertEquals("event-1", eventsAfterHead[0].first.id)
        Assertions.assertEquals("event-2", eventsAfterHead[1].first.id)
        Assertions.assertEquals("event-3", eventsAfterHead[2].first.id)

        // Test fetching after a specific token
        val token1 = eventStore.getTokenForEventId("event-1")!!
        val eventsAfterToken1 = eventStore.fetchEventsAfter(token1).toList()
        Assertions.assertEquals(2, eventsAfterToken1.size)
        Assertions.assertEquals("event-2", eventsAfterToken1[0].first.id)
        Assertions.assertEquals("event-3", eventsAfterToken1[1].first.id)

        // Test fetching with limit
        val eventsWithLimit = eventStore.fetchEventsAfter(headToken, 2).toList()
        Assertions.assertEquals(2, eventsWithLimit.size)
        Assertions.assertEquals("event-1", eventsWithLimit[0].first.id)
        Assertions.assertEquals("event-2", eventsWithLimit[1].first.id)
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
        Assertions.assertEquals(3, events.size)
        Assertions.assertEquals("event-1", events[0].first.id)
        Assertions.assertEquals("event-2", events[1].first.id)
        Assertions.assertEquals("event-3", events[2].first.id)
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
        Assertions.assertEquals(3, events.size)
        Assertions.assertEquals("event-1", events[0].first.id)
        Assertions.assertEquals("event-2", events[1].first.id)
        Assertions.assertEquals("event-3", events[2].first.id)
    }

    @Test
    fun `test publish adds event to store`() = runBlocking {
        val event = createTestEvent(id = "test-event")
        eventStore.publish(event)

        // Verify the event was added by fetching it
        val events = eventStore.fetchEventsAfter(eventStore.getHeadToken()).toList()
        Assertions.assertEquals(1, events.size)
        Assertions.assertEquals("test-event", events[0].first.id)
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