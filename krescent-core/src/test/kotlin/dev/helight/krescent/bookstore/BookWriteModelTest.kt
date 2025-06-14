@file:Suppress("unused")

package dev.helight.krescent.bookstore

import dev.helight.krescent.checkpoint.AlwaysCheckpointStrategy
import dev.helight.krescent.checkpoint.CheckpointStorage
import dev.helight.krescent.checkpoint.CheckpointStrategy
import dev.helight.krescent.checkpoint.impl.InMemoryCheckpointStorage
import dev.helight.krescent.event.Event
import dev.helight.krescent.model.EventModelBuilder
import dev.helight.krescent.model.ReducingWriteModel
import dev.helight.krescent.model.WriteModelBase
import dev.helight.krescent.model.WriteModelBase.Extension.handles
import dev.helight.krescent.model.WriteModelBase.Extension.withSource
import dev.helight.krescent.model.useState
import dev.helight.krescent.source.impl.InMemoryEventStore
import dev.helight.krescent.synchronization.KrescentLockProvider
import dev.helight.krescent.synchronization.LocalSharedLockProvider
import dev.helight.krescent.synchronization.ModelLockTransactionHandler.Extensions.useTransaction
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BookWriteModelTest {

    @Test
    fun `Test reconstruction of a simple write model`() = runBlocking {
        val lockProvider = LocalSharedLockProvider()
        val stream = InMemoryEventStore(bookstoreSimulatedEventStream.toMutableList())
        BookWriteModel("1", lockProvider).withSource(stream) handles {
            assertTrue(canBeLent(1))
            assertTrue(canBeLent(9))
            assertFalse(canBeLent(10))
        }
    }

    @Test
    fun `Test multiple writes using write models`(): Unit = runBlocking {
        val lockProvider = LocalSharedLockProvider()
        val stream = InMemoryEventStore(bookstoreSimulatedEventStream.toMutableList())
        BookWriteModel("1", lockProvider).withSource(stream) handles {
            removeCopies(4)
        }
        BookWriteModel("1", lockProvider).withSource(stream) handles {
            removeCopies(4)
        }
        assertThrows<Throwable> {
            BookWriteModel("1", lockProvider).withSource(stream) handles {
                removeCopies(4)
            }
        }
        BookWriteModel("1", lockProvider).withSource(stream) handles {
            assertEquals(1, available)
        }
    }

    @Test
    fun `Test racing writes being prevented by a lock provider`(): Unit = runBlocking {
        val lockProvider = LocalSharedLockProvider()
        val stream = InMemoryEventStore(bookstoreSimulatedEventStream.toMutableList())
        var isWriting = false

        suspend fun racingFun(lockProvider: KrescentLockProvider): Boolean {
            var wasWellBehaved = true
            BookWriteModel("1", lockProvider).withSource(stream) handles {
                if (isWriting) {
                    wasWellBehaved = false
                }
                isWriting = true
                delay(50)
                removeCopies(1)
                isWriting = false
            }
            return wasWellBehaved
        }

        assertTrue(
            listOf(
                async { racingFun(lockProvider) },
                async { racingFun(lockProvider) },
                async { racingFun(lockProvider) },
                async { racingFun(lockProvider) },
            ).awaitAll().all { it })

        // Sanity check to ensure that the lock provider is actually working
        assertFalse(
            listOf(
                async { racingFun(LocalSharedLockProvider()) },
                async { racingFun(LocalSharedLockProvider()) },
                async { racingFun(LocalSharedLockProvider()) },
                async { racingFun(LocalSharedLockProvider()) },
            ).awaitAll().all { it })
    }

    @Test
    fun `Test checkpointing with reducer`(): Unit = runBlocking {
        val lockProvider = LocalSharedLockProvider()
        val stream = InMemoryEventStore(bookstoreSimulatedEventStream.toMutableList())
        val storage = InMemoryCheckpointStorage()
        ReducingBookWriteModel("1", lockProvider, stream)

        ReducingBookWriteModel("1", lockProvider, stream, storage).apply {
            build(stream).catchup()
            useState {
                assertEquals(state.available, 9)
                assertEquals(eventsRead, bookOneEvtCount)
            }
        }

        ReducingBookWriteModel("1", lockProvider, stream, storage).apply {
            build(stream).restore()
            useState {
                assertEquals(state.available, 9)
                assertEquals(eventsRead, 0)
            }
        }
    }

    @Test
    fun `Test writes using a reducing write model`(): Unit = runBlocking {
        val lockProvider = LocalSharedLockProvider()
        val stream = InMemoryEventStore()
        val storage = InMemoryCheckpointStorage()
        ReducingBookWriteModel("1", lockProvider, stream, storage) handles {
            assertFalse(doesExist)
            create("Book One", "Author One", 9.99, 10)
            useState {
                assertEquals(10, state.available)
                assertEquals("Book One", state.book?.title)
            }
        }

        ReducingBookWriteModel("1", lockProvider, stream, storage) handles {
            assertTrue(doesExist)
            useState { assertEquals(10, state.available) }
            addCopies(5)
            useState { assertEquals(15, state.available) }
        }
    }

}

class BookWriteModel(
    val bookId: String,
    val lockProvider: KrescentLockProvider,
) : WriteModelBase("book_write", 1, bookstoreEventCatalog, configure = {
    useTransaction(lockProvider, "book-$bookId")
}) {

    var book: BookState? = null
    var available: Int = 0


    override suspend fun process(event: Event) {
        if (event !is BookEvent || event.bookId != bookId) return
        when (event) {
            is BookAddedEvent -> {
                book = BookState(
                    title = event.title, author = event.author, price = event.price, copies = event.copies
                )
                available = event.copies
            }

            is BookRemovedEvent -> {
                book = null
                available = 0
            }

            is BookPriceChangedEvent -> book?.let {
                book = it.copy(price = event.price)
            }

            is BookCopyAddedEvent -> book?.let {
                book = it.copy(copies = it.copies + event.copiesAdded)
                available += event.copiesAdded
            }

            is BookCopyRemovedEvent -> book?.let {
                book = it.copy(copies = it.copies - event.copiesRemoved)
                available -= event.copiesRemoved
            }

            is BookLentEvent -> available--
            is BookReturnedEvent -> available++
        }
    }

    suspend fun lend(userId: String) {
        if (canBeLent(1)) error("Not book available to be lent.")
        emitEvent(BookLentEvent(bookId, userId, "2025-1-1"))
        available--
    }

    suspend fun returnBook(userId: String) {
        // Just don't check if this user actually borrowed the book
        emitEvent(BookReturnedEvent(bookId, userId, "2025-1-1"))
        available++
    }

    suspend fun removeCopies(count: Int) {
        if (available < count) error("Not enough copies available to remove.")
        emitEvent(BookCopyRemovedEvent(bookId, count))
        available -= count
    }

    suspend fun addCopies(count: Int) {
        emitEvent(BookCopyAddedEvent(bookId, count))
        available += count
    }

    fun canBeLent(count: Int): Boolean {
        return available >= count
    }
}

class ReducingBookWriteModel(
    val bookId: String,
    val lockProvider: KrescentLockProvider,
    source: InMemoryEventStore,
    val checkpointStorage: CheckpointStorage? = null,
    val checkpointStrategy: CheckpointStrategy = AlwaysCheckpointStrategy,
) : ReducingWriteModel<ReducingBookWriteModel.State>("test", 1, bookstoreEventCatalog, source, configure = {
    useTransaction(lockProvider, "book-$bookId")
}) {

    var eventsRead: Int = 0

    override val initialState: State
        get() = State()

    override suspend fun EventModelBuilder.configure() {
        if (checkpointStorage != null) useCheckpoints(checkpointStorage, checkpointStrategy)
    }

    override suspend fun reduce(state: State, event: Event): State {
        if (event !is BookEvent || event.bookId != bookId) return state
        eventsRead++
        return when (event) {
            is BookAddedEvent -> state.copy(
                book = BookState(
                    title = event.title, author = event.author, price = event.price, copies = event.copies
                ), available = event.copies
            )

            is BookRemovedEvent -> state.copy(
                book = null, available = 0
            )

            is BookPriceChangedEvent -> state.copy(
                book = state.book!!.copy(price = event.price),
            )

            is BookCopyAddedEvent -> state.copy(
                book = state.book!!.copy(copies = state.book.copies + event.copiesAdded),
                available = state.available + event.copiesAdded
            )

            is BookCopyRemovedEvent -> state.copy(
                book = state.book!!.copy(copies = state.book.copies - event.copiesRemoved),
                available = state.available - event.copiesRemoved
            )

            is BookLentEvent -> state.copy(available = state.available - 1)
            is BookReturnedEvent -> state.copy(available = state.available + 1)
            else -> state
        }
    }

    suspend fun lend(userId: String) = useState {
        if (canBeLent(1)) error("Not book available to be lent.")
        emitEvent(BookLentEvent(bookId, userId, "2025-1-1"))
        state.copy(available = state.available - 1).push()
    }

    suspend fun returnBook(userId: String) = useState {
        // Just don't check if this user actually borrowed the book
        emitEvent(BookReturnedEvent(bookId, userId, "2025-1-1"))
        state.copy(available = state.available + 1).push()
    }

    suspend fun removeCopies(count: Int) = useState {
        if (state.available < count) error("Not enough copies available to remove.")
        emitEvent(BookCopyRemovedEvent(bookId, count))
        state.copy(available = state.available - count).push()
    }

    suspend fun addCopies(count: Int) = useState {
        emitEvent(BookCopyAddedEvent(bookId, count))
        state.copy(available = state.available + count).push()
    }

    suspend fun create(title: String, author: String, price: Double, copies: Int) = useState {
        if (state.book != null) error("Book already exists.")
        emitEvent(BookAddedEvent(bookId, title, author, price, copies))
        state.copy(
            book = BookState(title, author, price, copies), available = copies
        ).push()
    }

    fun canBeLent(count: Int): Boolean = useState {
        state.available >= count
    }

    val doesExist: Boolean
        get() = useState { state.book != null }

    @Serializable
    data class State(
        val book: BookState? = null,
        val available: Int = 0,
    )
}
