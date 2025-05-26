package dev.helight.krescent.bookstore

import dev.helight.krescent.event.Event
import dev.helight.krescent.model.EventModelBase.Extension.withConfiguration
import dev.helight.krescent.model.ReadModelBase.Extension.catchup
import dev.helight.krescent.model.ReducingReadModel
import dev.helight.krescent.source.impl.InMemoryEventStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlin.test.Test

class BookCountReadModelTest {

    @Test
    fun `Test my other model shit`() = runBlocking {
        BooksAvailableReadModel().withConfiguration {

        }.catchup(InMemoryEventStore())
    }
}

class BooksAvailableReadModel() : ReducingReadModel<BooksAvailableReadModel.State>(
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

    @Serializable
    data class State(
        val available: Map<String, Int> = emptyMap(),
    )
}