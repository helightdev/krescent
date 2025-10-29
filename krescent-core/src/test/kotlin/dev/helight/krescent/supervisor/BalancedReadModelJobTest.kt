package dev.helight.krescent.supervisor

import dev.helight.krescent.bookstore.BooksAvailableReadModel
import dev.helight.krescent.bookstore.bookstoreSimulatedEventStream
import dev.helight.krescent.source.impl.InMemoryEventStore
import kotlinx.coroutines.*
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class BalancedReadModelJobTest {

    @Test
    fun `BalancedReadModelJob processes bookstore events`() = runBlocking {
        withTimeout(1000) {
            val source = InMemoryEventStore(bookstoreSimulatedEventStream.toMutableList())

            val target = mutableMapOf<String, Int>()
            val crashCounter = AtomicInteger(1)
            var creationCounter = 0
            val job = BalancedReadModelJob(
                modelSupplier = {
                    creationCounter++
                    BooksAvailableReadModel(target, crashCounter)
                },
                source = source,
                maxRetryDelay = 20.toDuration(DurationUnit.MILLISECONDS),
            )

            val supervisor = ModelSupervisor()
            supervisor.register(job)

            val supJob = async { supervisor.execute() }

            // Give the supervisor time to perform catchup and start streaming
            delay(500)

            // Stop the supervisor and ensure it finished
            supJob.cancelAndJoin()

            // Expect book 1 -> 9 copies, book 2 -> 5 copies (as per simulated stream)
            assertEquals(mapOf("1" to 9, "2" to 5), target)
            assertEquals(3, creationCounter)
        }
    }

    @Test
    fun `BalancedReadModelJob panics after many crashes`(): Unit = runBlocking {
        withTimeout(1000) {
            val source = InMemoryEventStore(bookstoreSimulatedEventStream.toMutableList())

            val target = mutableMapOf<String, Int>()
            val crashCounter = AtomicInteger(20)
            val job = BalancedReadModelJob(
                modelSupplier = {
                    BooksAvailableReadModel(target, crashCounter)
                },
                source = source,
                maxRetryDelay = 20.toDuration(DurationUnit.MILLISECONDS),
            )

            val supervisor = ModelSupervisor()
            supervisor.register(job)

            // Stop the supervisor and ensure it finished
            assertThrows<SupervisorPanicException> { supervisor.execute() }
        }
    }
}
