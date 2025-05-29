package dev.helight.krescent.source.impl

import dev.helight.krescent.event.EventMessage
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonNull
import org.junit.jupiter.api.Assertions
import kotlin.test.Test

class ChronoBufferedMergeStreamTest {

    @Test
    fun test() = runBlocking {
        val a = InMemoryEventStore(
            mutableListOf(
                EventMessage(type = "1", payload = JsonNull, timestamp = Instant.fromEpochMilliseconds(1)),
                EventMessage(type = "5", payload = JsonNull, timestamp = Instant.fromEpochMilliseconds(5)),
            )
        )

        val b = InMemoryEventStore(
            mutableListOf(
                EventMessage(type = "2", payload = JsonNull, timestamp = Instant.fromEpochMilliseconds(2)),
                EventMessage(type = "6", payload = JsonNull, timestamp = Instant.fromEpochMilliseconds(6)),
            )
        )

        val c = InMemoryEventStore(
            mutableListOf(
                EventMessage(type = "3", payload = JsonNull, timestamp = Instant.fromEpochMilliseconds(3)),
                EventMessage(type = "4", payload = JsonNull, timestamp = Instant.fromEpochMilliseconds(4)),
            )
        )
        val merged = ChronoBufferedMergeStreamEventSource.Companion.create(listOf(a, b, c)).fetchEventsAfter().toList()
        Assertions.assertTrue(merged.mapIndexed { index, pair -> pair.first.type == "${index + 1}" }.all { it })
    }

}