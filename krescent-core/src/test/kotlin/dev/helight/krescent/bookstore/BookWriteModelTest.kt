@file:Suppress("unused")

package dev.helight.krescent.bookstore

import dev.helight.krescent.event.Event
import dev.helight.krescent.models.WriteModelBase
import dev.helight.krescent.models.WriteModelBase.Extension.handles
import dev.helight.krescent.source.impl.InMemoryEventStore
import dev.helight.krescent.synchronization.KrescentLockProvider
import dev.helight.krescent.synchronization.LocalSharedLockProvider
import dev.helight.krescent.synchronization.ModelLockTransactionHandler.Extensions.useTransaction
import kotlinx.coroutines.*
import org.junit.jupiter.api.assertThrows
import kotlin.test.*

class BookWriteModelTest {

    @Test
    fun `Test basic`() = runBlocking {
        val lockProvider = LocalSharedLockProvider()
        val stream = InMemoryEventStore(bookstoreSimulatedEventStream.toMutableList())
        BookWriteModel("1", lockProvider, stream) handles {
            assertTrue(canBeLent(1))
            assertTrue(canBeLent(9))
            assertFalse(canBeLent(10))
        }
    }

    @Test
    fun `Test multiple writes`(): Unit = runBlocking {
        val lockProvider = LocalSharedLockProvider()
        val stream = InMemoryEventStore(bookstoreSimulatedEventStream.toMutableList())
        BookWriteModel("1", lockProvider, stream) handles {
            removeCopies(4)
        }
        BookWriteModel("1", lockProvider, stream) handles {
            removeCopies(4)
        }
        assertThrows<Throwable> {
            BookWriteModel("1", lockProvider, stream) handles {
                removeCopies(4)
            }
        }
        BookWriteModel("1", lockProvider, stream) handles {
            assertEquals(1, available)
        }
    }

    @Test
    fun `Test racing writes`(): Unit = runBlocking {
        val lockProvider = LocalSharedLockProvider()
        val stream = InMemoryEventStore(bookstoreSimulatedEventStream.toMutableList())
        var isWriting = false

        suspend fun racingFun(lockProvider: KrescentLockProvider): Boolean {
            var wasWellBehaved = true
            BookWriteModel("1", lockProvider, stream) handles {
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

        assertTrue(listOf(
            async { racingFun(lockProvider) },
            async { racingFun(lockProvider) },
            async { racingFun(lockProvider) },
            async { racingFun(lockProvider) },
        ).awaitAll().all { it })

        // Sanity check to ensure that the lock provider is actually working
        assertFalse(listOf(
            async { racingFun(LocalSharedLockProvider()) },
            async { racingFun(LocalSharedLockProvider()) },
            async { racingFun(LocalSharedLockProvider()) },
            async { racingFun(LocalSharedLockProvider()) },
        ).awaitAll().all { it })
    }

}

class BookWriteModel(
    val bookId: String,
    val lockProvider: KrescentLockProvider,
    source: InMemoryEventStore,
) : WriteModelBase("test", 1, bookstoreEventCatalog, source, configure = {
    useTransaction(lockProvider, "book-$bookId")
}) {

    var book: BookState? = null
    var available: Int = 0

    override suspend fun process(event: Event) {
        if (event !is BookEvent || event.bookId != bookId) return
        when (event) {
            is BookAddedEvent -> {
                book = BookState(
                    title = event.title,
                    author = event.author,
                    price = event.price,
                    copies = event.copies
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

    fun lend(userId: String) {
        if (canBeLent(1)) error("Not book available to be lent.")
        emitEvent(BookLentEvent(bookId, userId, "2025-1-1"))
        available--
    }

    fun returnBook(userId: String) {
        // Just don't check if this user actually borrowed the book
        emitEvent(BookReturnedEvent(bookId, userId, "2025-1-1"))
        available++
    }

    fun removeCopies(count: Int) {
        if (available < count) error("Not enough copies available to remove.")
        emitEvent(BookCopyRemovedEvent(bookId, count))
        available -= count
    }

    fun addCopies(count: Int) {
        emitEvent(BookCopyAddedEvent(bookId, count))
        available += count
    }

    fun canBeLent(count: Int): Boolean {
        return available >= count
    }
}
