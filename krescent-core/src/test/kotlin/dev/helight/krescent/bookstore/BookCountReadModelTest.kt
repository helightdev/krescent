package dev.helight.krescent.bookstore

import dev.helight.krescent.checkpoint.AlwaysCheckpointStrategy
import dev.helight.krescent.checkpoint.MinimizedCheckpointStrategy
import dev.helight.krescent.checkpoint.impl.InMemoryCheckpointStorage
import dev.helight.krescent.event.Event
import dev.helight.krescent.model.EventModelBase.Extension.withConfiguration
import dev.helight.krescent.model.ReadModelBase.Extension.catchup
import dev.helight.krescent.model.ReadModelBase.Extension.restoreOnly
import dev.helight.krescent.model.ReadModelBase.Extension.stream
import dev.helight.krescent.model.ReducingReadModel
import dev.helight.krescent.source.impl.InMemoryEventStore
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds

class BookCountReadModelTest {

    @Test
    fun `Test runs without exception`(): Unit = runBlocking {
        BooksAvailableReadModel().withConfiguration {

        }.catchup(InMemoryEventStore())
    }

    @Test
    fun `Test restore only operation`(): Unit = runBlocking {
        val source = InMemoryEventStore()
        val checkpointStorage = InMemoryCheckpointStorage()
        source.publishAll(bookstoreSimulatedEventStream)

        val initial = BooksAvailableReadModel().withConfiguration {
            useCheckpoints(checkpointStorage, AlwaysCheckpointStrategy)
        }.restoreOnly()
        assertNull(initial)

        val caughtUp = BooksAvailableReadModel().withConfiguration {
            useCheckpoints(checkpointStorage, AlwaysCheckpointStrategy)
        }.catchup(source)
        assertEquals(9, caughtUp.target["1"])

        val late = BooksAvailableReadModel().withConfiguration {
            useCheckpoints(checkpointStorage, AlwaysCheckpointStrategy)
        }.restoreOnly()
        assertNotNull(late)
        assertEquals(9, late.target["1"])
    }

    @Test
    fun `Test minimized checkpointing`(): Unit = runBlocking {
        val source = InMemoryEventStore()
        val checkpointStorage = InMemoryCheckpointStorage()
        source.publishAll(bookstoreSimulatedEventStream)

        val initial = BooksAvailableReadModel().withConfiguration {
            useCheckpoints(checkpointStorage, MinimizedCheckpointStrategy())
        }.restoreOnly()
        assertNull(initial)
        assertNull(checkpointStorage.getLatestCheckpoint("books.counts"))

        // Check if it happens at termination / catchup
        val caughtUp = BooksAvailableReadModel().withConfiguration {
            useCheckpoints(checkpointStorage, MinimizedCheckpointStrategy())
        }.catchup(source)
        assertEquals(9, caughtUp.target["1"])
        assertNotNull(checkpointStorage.getLatestCheckpoint("books.counts"))

        // Check if it happens at the callback as well
        checkpointStorage.clearCheckpoints()
        assertNull(checkpointStorage.getLatestCheckpoint("books.counts"))
        val job = async {
            BooksAvailableReadModel().withConfiguration {
                useCheckpoints(checkpointStorage, MinimizedCheckpointStrategy())
            }.stream(source)
        }
        delay(200.milliseconds)
        assertNotNull(checkpointStorage.getLatestCheckpoint("books.counts"))
        job.cancelAndJoin()
    }
}

class BooksAvailableReadModel(
    val target: MutableMap<String, Int> = mutableMapOf(),
    val crashCount: AtomicInteger = AtomicInteger(0),
) : ReducingReadModel<BooksAvailableReadModel.State>(
    namespace = "books.counts",
    revision = 1,
    catalog = bookstoreEventCatalog,
) {

    override val initialState: State
        get() = State()

    override suspend fun reduce(
        state: State,
        event: Event,
    ): State = when (event) {
        is BookAddedEvent -> state.copy(
            available = state.available + (event.bookId to event.copies)
        )

        is BookRemovedEvent -> state.copy(
            available = state.available - event.bookId
        )

        is BookCopyAddedEvent -> state.copy(
            available = state.available + (event.bookId to (state.available[event.bookId] ?: 0) + event.copiesAdded)
        )

        is BookCopyRemovedEvent -> state.copy(
            available = state.available + (event.bookId to (state.available[event.bookId] ?: 0) - event.copiesRemoved)
        )

        is BookLentEvent -> state.copy(
            available = state.available + (event.bookId to (state.available[event.bookId] ?: 0) - 1)
        )

        is BookReturnedEvent -> state.copy(
            available = state.available + (event.bookId to (state.available[event.bookId] ?: 0) + 1)
        )

        else -> state
    }

    override suspend fun process(event: Event) {
        if (crashCount.get() > 0) {
            crashCount.decrementAndGet()
            error("Simulated crash")
        }

        super.process(event)
        target.clear()
        target.putAll(currentState.available)
    }

    @Serializable
    data class State(
        val available: Map<String, Int> = emptyMap(),
    )
}