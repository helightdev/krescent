package dev.helight.krescent.situations

import dev.helight.krescent.bookstore.BookAddedEvent
import dev.helight.krescent.bookstore.bookstoreEventCatalog
import dev.helight.krescent.checkpoint.AlwaysCheckpointStrategy
import dev.helight.krescent.checkpoint.FixedEventRateCheckpointStrategy
import dev.helight.krescent.checkpoint.impl.InMemoryCheckpointStorage
import dev.helight.krescent.model.buildEventModel
import dev.helight.krescent.source.impl.InMemoryEventStore
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals

class CheckpointingSituations {

    @Test
    fun `Errors do not invalidate and the latest checkpoint is reloaded`() = runBlocking {
        val store = InMemoryEventStore()
        val checkpoints = InMemoryCheckpointStorage()
        store.publish(bookstoreEventCatalog.create(BookAddedEvent("1", "", "", 1.0, 1)))
        store.publish(bookstoreEventCatalog.create(BookAddedEvent("1", "", "", 1.0, 1)))

        var counter = 0
        assertThrows<Exception> {
            store.buildEventModel("my-model", 1, bookstoreEventCatalog) {
                useCheckpoints(checkpoints, AlwaysCheckpointStrategy)
                handler {
                    if (it is BookAddedEvent) {
                        counter++
                        if (counter == 2) error("Simulated failure")
                    }
                }
            }.stream()
        }
        assertEquals(counter, 2)

        store.buildEventModel("my-model", 1, bookstoreEventCatalog) {
            useCheckpoints(checkpoints, FixedEventRateCheckpointStrategy(100))
            handler {
                if (it is BookAddedEvent) counter++
            }
        }.catchup()
        assertEquals(counter, 3)
    }

    @Test
    fun `Graceful stream interruption with terminal checkpointing`() = runBlocking {
        val store = InMemoryEventStore()
        val checkpoints = InMemoryCheckpointStorage()
        var counter = 0
        val job = async {
            store.buildEventModel("my-model", 1, bookstoreEventCatalog) {
                useCheckpoints(checkpoints, FixedEventRateCheckpointStrategy(100))
                handler {
                    if (it is BookAddedEvent) counter++
                }
            }.stream()
        }
        store.publish(bookstoreEventCatalog.create(BookAddedEvent("1", "", "", 1.0, 1)))
        store.publish(bookstoreEventCatalog.create(BookAddedEvent("1", "", "", 1.0, 1)))
        delay(50)
        job.cancelAndJoin()
        store.publish(bookstoreEventCatalog.create(BookAddedEvent("1", "", "", 1.0, 1)))
        assertEquals(counter, 2)
        store.buildEventModel("my-model", 1, bookstoreEventCatalog) {
            useCheckpoints(checkpoints, FixedEventRateCheckpointStrategy(100))
            handler {
                if (it is BookAddedEvent) counter++
            }
        }.catchup()
        assertEquals(counter, 3)
    }


}