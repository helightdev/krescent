---
title: Models
---

# Models

Models in Krescent are the core components of the framework yet relatively loosely defined. In essence, they are
end-user flavored event handlers with additional syntactic sugar. While there is a base `EventModelBase`, you will
mostly
be using flavors of the `ReadModelBase` and the `WriteModelBase` classes, so let's take a look at them.

## Read Model

A read model processes events to provide data accessibly for querying or reporting. Writing data to a target outside the
read model is referred to as projection, since it reduces the event stream to a small more specialized form.

Krescent provides various ways to project data, like in memory projection to some premade collections and custom
serializable data structures or projections to a database like MongoDB.

#### Example Read Model

```kotlin
class AllAvailableBooksReadModel(
    collectionName: String,
    database: MongoDatabase,
) : ReadModelBase(
    "book.all_available_books", 1, bookstoreEventCatalog
) {

    val collection by mongoCollectionProjection(
        collectionName, database,
        allowBatching = true
    )

    override suspend fun process(event: Event) {
        when (event) {
            // Handle specific events here
        }
    }
}
```

> You can see that the read models constructor defines all dependencies that we need to start this model, excluding the
> event source in this case. In this case, we are using the `mongoCollectionProjection` function to automatically create
> the corresponding model extension and automatically register it. The projection will automatically handle the
> initialization and checkpoint of the projection if we choose to enable it.

```kotlin
val mongoDatabase = mongoClient.getDatabase("my_database")
val eventSource = KurrentEventSource(kurrentClient, "my_event_stream")

AllAvailableBooksReadModel( // [!code focus:5]
    collectionName = "available_books",
    database = mongoDatabase
).stream(eventSource)
// Will stream until interrupted
```

> The `stream` function will start the model and begin processing events from the event source. It will run until
> interrupted, processing events as they come in and updating the projection accordingly.

```kotlin
val mongoCheckpointStorage = MongoCheckpointStorage(mongoDatabase)

AllAvailableBooksReadModel(
    collectionName = "available_books",
    database = mongoDatabase
).withConfiguration { // [!code focus:6]
    useCheckpoints(
        mongoCheckpointStorage,
        FixedTimeRateCheckpointStrategy(10.toDuration(DurationUnit.SECONDS))
    )
}.stream(eventSource)
```

> We can perform additional configuration before streaming the model. In this case, we are enabling checkpoints
> using the `MongoCheckpointStorage` and a fixed time rate checkpoint strategy that will create a snapshot
> if the last event is older than 10 seconds.

> [!NOTE]
> Configuration done outside the class should usually only change behavior not directly relevant to the model's logic.
> You may setup transaction support, checkpointing, logging or similar features here when possible.

## Write Model

Similar to read models, write models also process events to create a state, but they are mostly focused on a
single entity or a small set of entities instead of whole domains. They are generally also not continuous, being
only materialized when needed for a write operation and mostly also not checkpointed.

Usually, write models establish transactional boundaries in one way or another so that writes do not interfere with
each other. Krescent currently only supports fine-grained pessimistic locking. Locking is normally done on the
entity id or related entity ids, similar to [Dynamic Consistency Boundaries](https://dcb.events/).

#### Example Write Model

```kotlin
// [!code focus:8]
class BookWriteModel(
    val bookId: String,
    val lockProvider: KrescentLockProvider,
) : WriteModelBase("book_write", 1, bookstoreEventCatalog, configure = {
    // Configure here directly to save on some boilerplate
    useTransaction(lockProvider, "book-$bookId")
}) {

    var book: BookState? = null
    var available: Int = 0

    override suspend fun process(event: Event) {
        if (event !is BookEvent || event.bookId != bookId) return
        when (event) {
            // Handle replaying the state from events
        }
    }

    suspend fun lend(userId: String) { // [!code focus:5]
        if (available <= 0) error("No book available to be lent.")
        emitEvent(BookLentEvent(bookId, userId, "now"))
        available--
    }

    suspend fun returnBook(userId: String) {
        emitEvent(BookReturnedEvent(bookId, userId, "now"))
        available++
    }
}
```

> Just as with the read model, we extend the `EventModelBase` using the `WriteModelBase` flavor and define our state
> variables and event processing logic. But additionally, we also define methods to alter the state of the current
> model.
> To configure transactional boundaries, we use the configuration block in the inherited constructor to separate
> configuration and model logic a bit clearer. We could also configure this externally or using the open `configure`
> function of the `EventModelBase`.

````kotlin
val lockProvider = LocalSharedLockProvider()
val eventSource = KurrentEventSource(kurrentClient, "my_event_stream")

BookWriteModel("1", lockProvider).withSource(eventSource) handles { // [!code focus:3]
    lend("250cd9d5-2f6b-4831-bb2e-ed9a46e9cc08")
}
````

> We now attach the event source to the model using the `withSource` builder function and then call the
> `handles` infix function to execute the model build. This will do a transactional catchup replay from the event
> source and then execute the specified block while still in transaction. We use this scope to execute the
> `lend` method to create a new event and emit it to the event source. After this operation, the events will be
> added to the event source and the transaction will be finished, freeing the lock that has been acquired.

## Reducing Models

Krescent also provides a reduce-style syntax for models, which automatically adds serialization for
checkpointing and enforces clearly visible state changes.

### Example Reducing Read Model

```kotlin
class BooksAvailableModel() : ReducingReadModel<BooksAvailableModel.State>(
    "book.available", 1, bookstoreEventCatalog
) {
    override val initialState: State
        get() = State()

    override suspend fun reduce(state: State, event: Event) = when (event) {
        is BookAddedEvent -> state.copy(
            available = state.available + (event.bookId to event.copies)
        )
        // Other events can be handled here
        else -> state
    }

    @Serializable
    data class State(
        val available: Map<String, Int> = emptyMap(),
    )
}
````

### Example Reducing Write Model

````kotlin
class ReducingBookWriteModel(
    val bookId: String,
    val lockProvider: KrescentLockProvider,
) : ReducingWriteModel<ReducingBookWriteModel.State>(
    "test", 1, bookstoreEventCatalog,
    configure = { useTransaction(lockProvider, "book-$bookId") }
) {
    override val initialState: State
        get() = State()

    override suspend fun reduce(state: State, event: Event): State {
        if (event !is BookEvent || event.bookId != bookId) return state
        return when (event) {
            is BookAddedEvent -> state.copy(
                book = BookState(
                    title = event.title, author = event.author,
                    price = event.price, copies = event.copies
                ), available = event.copies
            )
            // Handle other events here
            is BookLentEvent -> state.copy(available = state.available - 1)
            is BookReturnedEvent -> state.copy(available = state.available + 1)
            else -> state
        }
    }

    // [!code focus:7]
    suspend fun lend(userId: String) = useState {
        if (canBeLent(1)) error("No book available to be lent.")
        emitEvent(BookLentEvent(bookId, userId, "2025-1-1"))

        // Use .push() to update the internal state of the model
        state.copy(available = state.available - 1).push()
    }

    // [!code focus:4]
    // Use useState() to access the current state of the model as "state"
    fun canBeLent(count: Int): Boolean = useState {
        state.available >= count
    }

    @Serializable
    data class State(
        val book: BookState? = null,
        val available: Int = 0,
    )
}
````