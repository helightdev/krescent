package dev.helight.krescent.bookstore

import dev.helight.krescent.checkpoint.ManualCheckpointStrategy
import dev.helight.krescent.checkpoint.impl.InMemoryCheckpointStorage
import dev.helight.krescent.model.buildEventModel
import dev.helight.krescent.model.projection.MemoryProjection.Companion.memoryProjection
import dev.helight.krescent.source.impl.InMemoryEventStore
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.test.*

class ListedBooksReadModelTest {

    @Test
    fun `Test single large catchup`() = runBlocking {
        val buffer = mutableMapOf<String, BookState>()
        buffer.put(
            "3", BookState(
                title = "Left Overs",
                author = "",
                price = 1.0,
                copies = 1
            )
        )
        val source = InMemoryEventStore(bookstoreSimulatedEventStream.toMutableList())
        source.buildEventModel("books.listed", 1, bookstoreEventCatalog) {
            val view by memoryProjection(
                "projection", buffer,
                store = { Json.encodeToJsonElement(Snapshot(buffer)) },
                load = { data, _ ->
                    buffer.clear()
                    buffer.putAll(Json.decodeFromJsonElement<Snapshot>(data).data)
                },
                initialize = { buffer.clear() }
            )

            handler {
                when (it) {
                    is BookAddedEvent -> view.put(
                        it.bookId, BookState(
                            title = it.title,
                            author = it.author,
                            price = it.price,
                            copies = it.copies
                        )
                    )

                    is BookRemovedEvent -> view.remove(it.bookId)
                    is BookPriceChangedEvent -> view[it.bookId]?.let { book ->
                        view[it.bookId] = book.copy(price = it.price)
                    }

                    is BookCopyAddedEvent -> view[it.bookId]?.let { book ->
                        view[it.bookId] = book.copy(copies = book.copies + it.copiesAdded)
                    }
                }
            }
        }.catchup()
        assertEquals(2, buffer.size)
        assertEquals(finalBookStates["1"], buffer["1"])
        assertEquals(finalBookStates["2"], buffer["2"])
    }

    @Test
    fun `Test catchup delayed`() = runBlocking {
        val buffer = mutableMapOf<String, BookState>()
        buffer.put(
            "3", BookState(
                title = "Left Overs",
                author = "",
                price = 1.0,
                copies = 1
            )
        )
        val source = InMemoryEventStore()
        source.publish(bookstoreSimulatedEventStream[0])
        val model = source.buildEventModel("books.listed", 1, bookstoreEventCatalog) {
            val view by memoryProjection(
                "projection", buffer,
                store = { Json.encodeToJsonElement(Snapshot(buffer)) },
                load = { data, _ ->
                    buffer.clear()
                    buffer.putAll(Json.decodeFromJsonElement<Snapshot>(data).data)
                },
                initialize = {
                    buffer.clear()
                }
            )

            handler {
                when (it) {
                    is BookAddedEvent -> view.put(
                        it.bookId, BookState(
                            title = it.title,
                            author = it.author,
                            price = it.price,
                            copies = it.copies
                        )
                    )

                    is BookRemovedEvent -> view.remove(it.bookId)
                    is BookPriceChangedEvent -> view[it.bookId]?.let { book ->
                        view[it.bookId] = book.copy(price = it.price)
                    }

                    is BookCopyAddedEvent -> view[it.bookId]?.let { book ->
                        view[it.bookId] = book.copy(copies = book.copies + it.copiesAdded)
                    }
                }
            }
        }
        model.catchup()
        assertEquals(1, buffer.size)

        source.publish(bookstoreSimulatedEventStream[1])
        model.catchup()
        assertEquals(2, buffer.size)

        source.publishAll(bookstoreSimulatedEventStream.drop(2))
        model.catchup()
        assertEquals(2, buffer.size)
    }


    @Test
    fun `Test streamed`() = runBlocking {
        val buffer = mutableMapOf<String, BookState>()
        buffer.put(
            "3", BookState(
                title = "Left Overs",
                author = "",
                price = 1.0,
                copies = 1
            )
        )
        val source = InMemoryEventStore()
        source.publish(bookstoreSimulatedEventStream[0])
        val model = source.buildEventModel("books.listed", 1, bookstoreEventCatalog) {
            val view by memoryProjection(
                "projection", buffer,
                store = { Json.encodeToJsonElement(Snapshot(buffer)) },
                load = { data, _ ->
                    buffer.clear()
                    buffer.putAll(Json.decodeFromJsonElement<Snapshot>(data).data)
                },
                initialize = {
                    buffer.clear()
                }
            )

            handler {
                when (it) {
                    is BookAddedEvent -> view.put(
                        it.bookId, BookState(
                            title = it.title,
                            author = it.author,
                            price = it.price,
                            copies = it.copies
                        )
                    )

                    is BookRemovedEvent -> view.remove(it.bookId)
                    is BookPriceChangedEvent -> view[it.bookId]?.let { book ->
                        view[it.bookId] = book.copy(price = it.price)
                    }

                    is BookCopyAddedEvent -> view[it.bookId]?.let { book ->
                        view[it.bookId] = book.copy(copies = book.copies + it.copiesAdded)
                    }
                }
            }
        }
        val streamingJob = launch {
            model.stream()
        }
        delay(50)
        assertEquals(1, buffer.size)

        source.publish(bookstoreSimulatedEventStream[1])
        delay(50)
        assertEquals(2, buffer.size)

        source.publishAll(bookstoreSimulatedEventStream.drop(2))
        delay(50)
        assertEquals(2, buffer.size)

        streamingJob.cancelAndJoin()
    }

    @Test
    fun `Test with Checkpoints`() = runBlocking {
        val buffer = mutableMapOf<String, BookState>()
        buffer.put(
            "3", BookState(
                title = "Left Overs",
                author = "",
                price = 1.0,
                copies = 1
            )
        )
        val source = InMemoryEventStore()
        val checkpointStorage = InMemoryCheckpointStorage()
        val manualCheckpointStrategy = ManualCheckpointStrategy()

        source.publish(bookstoreSimulatedEventStream[0])
        val model = source.buildEventModel("books.listed", 1, bookstoreEventCatalog) {
            useCheckpoints(checkpointStorage, manualCheckpointStrategy)

            val view by memoryProjection(
                "projection", buffer,
                store = { Json.encodeToJsonElement(Snapshot(buffer)) },
                load = { data, _ ->
                    buffer.clear()
                    buffer.putAll(Json.decodeFromJsonElement<Snapshot>(data).data)
                },
                initialize = {
                    buffer.clear()
                }
            )

            handler {
                when (it) {
                    is BookAddedEvent -> view.put(
                        it.bookId, BookState(
                            title = it.title,
                            author = it.author,
                            price = it.price,
                            copies = it.copies
                        )
                    )

                    is BookRemovedEvent -> view.remove(it.bookId)
                    is BookPriceChangedEvent -> view[it.bookId]?.let { book ->
                        view[it.bookId] = book.copy(price = it.price)
                    }

                    is BookCopyAddedEvent -> view[it.bookId]?.let { book ->
                        view[it.bookId] = book.copy(copies = book.copies + it.copiesAdded)
                    }
                }
            }
        }

        var streamingJob = launch {
            model.stream()
        }
        delay(50)
        assertEquals(1, buffer.size)
        assertNull(checkpointStorage.getLatestCheckpoint("books.listed"))

        // Checkpoint and normal event processing
        manualCheckpointStrategy.mark() // Checkpoint after the next event
        source.publish(bookstoreSimulatedEventStream[1])
        delay(50)
        assertEquals(2, buffer.size)
        assertNotNull(checkpointStorage.getLatestCheckpoint("books.listed"))

        // Publish the rest of the event stream, no checkpoint should be created here
        source.publishAll(bookstoreSimulatedEventStream.drop(2))
        delay(50)
        assertEquals(2, buffer.size)
        assertEquals(buffer["1"], finalBookStates["1"])
        assertEquals(buffer["2"], finalBookStates["2"])

        streamingJob.cancelAndJoin()

        // Check if the restored state matches the expected non-final state
        buffer.put(
            "3", BookState(
                title = "Left Overs",
                author = "",
                price = 1.0,
                copies = 1
            )
        )
        model.restore()
        delay(50)
        assertEquals(2, buffer.size)
        assertNotEquals(buffer["1"], finalBookStates["1"])


        // Check if streaming from the restored state results in the final state
        buffer.put(
            "3", BookState(
                title = "Left Overs",
                author = "",
                price = 1.0,
                copies = 1
            )
        )
        streamingJob = launch {
            model.stream()
        }
        delay(50)
        assertEquals(2, buffer.size)
        assertEquals(buffer["1"], finalBookStates["1"])
        assertEquals(buffer["2"], finalBookStates["2"])
        streamingJob.cancelAndJoin()
    }

    @Serializable
    data class Snapshot(
        val data: Map<String, BookState>,
    )
}