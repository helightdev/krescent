package dev.helight.krescent.test

import dev.helight.krescent.event.Event
import dev.helight.krescent.event.buildEventCatalog
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals

class StreamAssertionTest {

    @Test
    fun `Check next event and event counting`(): Unit = runBlocking {
        val stream = buildStream(sampleCatalog) {
            +EventA
            +EventB
            +EventC(42)
        }

        assertStream(sampleCatalog, stream) {
            val a = countAtLeast(2)
            assertEquals(2, a.size)

            val b = countExact(3)
            assertEquals(3, b.size)

            nextEvent<EventA>()
            val c = countAvailableExact(2)
            assertEquals(2, c.size)
            nextEvent<EventB>()
            nextEvent<EventC>()
        }

        // Wrong count
        assertStreamFails(sampleCatalog, stream) {
            countExact(2)
        }

        // Wrong order
        assertStreamFails(sampleCatalog, stream) {
            nextEvent<EventB>()
            nextEvent<EventA>()
            nextEvent<EventC>()
        }

        // Over-read
        assertStreamFails(sampleCatalog, stream) {
            nextEvent<EventA>()
            nextEvent<EventB>()
            nextEvent<EventC>()
            nextEvent<EventC>()
        }
    }

    @Test
    fun `Empty stream checks`(): Unit = runBlocking {
        val stream = buildStream(sampleCatalog) {}

        assertStream(sampleCatalog, stream) {
            countExact(0)
            countAvailableExact(0)
            countAtLeast(0)
            countAvailableAtLeast(0)
            hasNoEvent()
        }

        assertStreamFails(sampleCatalog, stream) { countAtLeast(1) }
        assertStreamFails(sampleCatalog, stream) { hasEvent() }
        assertStreamFails(sampleCatalog, stream) { nextEvent<EventA>() }
    }

    @Test
    fun `Verify event presence checks`(): Unit = runBlocking {
        val stream = buildStream(sampleCatalog) {
            +EventA
            +EventB
        }

        assertStream(sampleCatalog, stream) {
            hasEvent()
            hasEvent<EventA>()
            hasEvent<EventB>()
            hasNoEvent<EventC>()
        }

        assertStreamFails(sampleCatalog, stream) { hasNoEvent() }
        assertStreamFails(sampleCatalog, stream) { hasEvent<EventC>() }
    }

    @Test
    fun `Check matchers`(): Unit = runBlocking {
        val stream = buildStream(sampleCatalog) {
            +EventC(10)
            +EventC(20)
            +EventC(25)
        }

        assertStream(sampleCatalog, stream) {
            hasEvent<EventC> { it.value == 10 }
            hasEvent<EventC> { it.value == 20 }
            hasEvent<EventC> { it.value == 25 }
            hasNoEvent<EventC> { it.value >= 30 }
        }

        assertStream(sampleCatalog, stream) {
            nextEvent<EventC> { it.value == 10 }
            nextEvent<EventC> { it.value == 20 }
            nextEvent<EventC> { it.value == 25 }
        }

        assertStream(sampleCatalog, stream) {
            nextEvent<EventC> { it.value == 10 }
            nextEvent<EventC> { it.value == 20 }
            ifNextEvent<EventC> { it.value == 25 }
            ifNextEvent<EventC> { it.value == 25 }
        }

    }

}

val sampleCatalog = buildEventCatalog(1) {
    event<EventA>("event-a")
    event<EventB>("event-b")
    event<EventC>("event-c")
}

@Serializable
object EventA : Event()

@Serializable
object EventB : Event()

@Serializable
class EventC(val value: Int) : Event()