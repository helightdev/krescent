package dev.helight.krescent.bookstore

import dev.helight.krescent.event.Event
import dev.helight.krescent.event.EventMessage
import dev.helight.krescent.event.buildEventCatalog
import kotlinx.serialization.Serializable

@Serializable
data class BookAddedEvent(
    override val bookId: String,
    val title: String,
    val author: String,
    val price: Double,
    val copies: Int,
) : Event(), BookEvent

@Serializable
data class BookPriceChangedEvent(
    override val bookId: String,
    val price: Double,
) : Event(), BookEvent

@Serializable
data class BookRemovedEvent(
    override val bookId: String,
) : Event(), BookEvent

@Serializable
data class BookLentEvent(
    override val bookId: String,
    val userId: String,
    val lentDate: String,
) : Event(), BookEvent

@Serializable
data class BookReturnedEvent(
    override val bookId: String,
    val userId: String,
    val returnDate: String,
) : Event(), BookEvent

@Serializable
data class BookCopyAddedEvent(
    override val bookId: String,
    val copiesAdded: Int,
) : Event(), BookEvent

@Serializable
data class BookCopyRemovedEvent(
    override val bookId: String,
    val copiesRemoved: Int,
) : Event(), BookEvent

interface BookEvent {
    val bookId: String
}

val bookstoreEventCatalog = buildEventCatalog(1) {
    event<BookAddedEvent>("book.added")
    event<BookPriceChangedEvent>("book.price_changed")
    event<BookRemovedEvent>("book.removed")
    event<BookLentEvent>("book.lent")
    event<BookReturnedEvent>("book.returned")
    event<BookCopyAddedEvent>("book.copy_added")
    event<BookCopyRemovedEvent>("book.copy_removed")
}

val bookstoreSimulatedEventStream = listOf<EventMessage>(
    bookstoreEventCatalog.create(
        BookAddedEvent(
            bookId = "1",
            title = "Effective Kotlin",
            author = "Marcin Moskala",
            price = 29.99,
            copies = 10
        )
    ),
    bookstoreEventCatalog.create(
        BookAddedEvent(
            bookId = "2",
            title = "Kotlin in Action",
            author = "Dmitry Jemerov, Svetlana Isakova",
            price = 34.99,
            copies = 5
        )
    ),
    bookstoreEventCatalog.create(
        BookPriceChangedEvent(
            bookId = "1",
            price = 24.99
        )
    ),
    bookstoreEventCatalog.create(
        BookLentEvent(
            bookId = "1",
            userId = "user123",
            lentDate = "2023-10-01"
        )
    ),
    bookstoreEventCatalog.create(
        BookAddedEvent(
            bookId = "3",
            title = "Accidentally added this, oops",
            author = "admin",
            price = 99.99,
            copies = 1
        )
    ),
    bookstoreEventCatalog.create(
        BookRemovedEvent(
            bookId = "3"
        )
    ),
    bookstoreEventCatalog.create(
        BookLentEvent(
            bookId = "1",
            userId = "user456",
            lentDate = "2023-10-02"
        )
    ),
    bookstoreEventCatalog.create(
        BookReturnedEvent(
            bookId = "1",
            userId = "user123",
            returnDate = "2023-10-10"
        )
    ),
)

@Serializable
data class BookState(
    val title: String,
    val author: String,
    val price: Double,
    val copies: Int,
)


val finalBookStates = mapOf(
    "1" to BookState(
        title = "Effective Kotlin",
        author = "Marcin Moskala",
        price = 24.99,
        copies = 10
    ),
    "2" to BookState(
        title = "Kotlin in Action",
        author = "Dmitry Jemerov, Svetlana Isakova",
        price = 34.99,
        copies = 5
    )
)

val bookOneEvtCount = 5