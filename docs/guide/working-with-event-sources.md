---
title: Working with Event Sources
outline: deep
---

Event Sources are fundamental to Krescent, providing the mechanism for reading and writing event streams. The primary interfaces are `StreamingEventSource` for consuming events and `EventPublisher` for adding new events to a stream. Often, a single implementation (like `InMemoryEventStore` or `MongoEventStore`) will implement both interfaces.

## Common `StreamingEventSource` Operations

The `StreamingEventSource` interface provides several methods to access events from the underlying store:

-   **`fetchEventsAfter(token: StreamingToken, limit: Int): List<EventMessage>`**:
    This method retrieves a batch of event messages that occurred *after* the position indicated by the given `StreamingToken`. The `limit` parameter controls the maximum number of events to fetch. This is useful for paginating through historical events or for fetching a specific range.

-   **`streamEvents(startToken: StreamingToken): Flow<EventMessage>`**:
    This function returns a Kotlin `Flow` that provides a continuous stream of event messages. It typically starts by emitting historical events from the `startToken` onwards and then continues to emit new events as they are published to the source. This is the primary way event models consume events in real-time.

-   **`getHeadToken(): StreamingToken`**:
    Returns a `StreamingToken` that represents the very beginning (the oldest available event) of the event stream.

-   **`getTailToken(): StreamingToken`**:
    Returns a `StreamingToken` that represents the current end (the most recent event) of the event stream at the time of the call.

-   **`deserializeToken(encoded: String): StreamingToken`**:
    `StreamingToken`s are opaque objects but can be serialized to a string representation for persistence (e.g., when checkpointing an event model's progress). This method allows you to convert such a string back into a `StreamingToken` object to resume processing from a saved position.

## Provided Implementations

Krescent offers several out-of-the-box implementations for event sourcing and publishing.

### `InMemoryEventStore`

-   **Use Cases**: Excellent for testing, examples, rapid prototyping, or very simple applications where event persistence across application restarts is not required.
-   **Functionality**: Implements both `StreamingEventSource` and `EventPublisher`.
-   **Persistence**: Stores events in memory. All events are lost when the application stops or the `InMemoryEventStore` instance is garbage collected.
-   **Instantiation**:
    ```kotlin
    import dev.helight.krescent.source.impl.InMemoryEventStore

    val inMemoryEventStore = InMemoryEventStore()
    // Ready to be used as both a StreamingEventSource and EventPublisher
    ```
    Its basic usage is also demonstrated in the [Getting Started](/guide/getting-started.md) guide.

### `krescent-mongo` (MongoDB)

-   **Use Cases**: For applications requiring a persistent event store backed by MongoDB.
-   **Functionality**: Provides `MongoEventStore` which implements `StreamingEventSource` and `EventPublisher`.
-   **Setup**: Requires a running MongoDB instance and the `krescent-mongo` dependency.
    ```gradle
    // build.gradle.kts
    dependencies {
        implementation("dev.helight.krescent:krescent-mongo:LATEST_VERSION")
    }
    ```
-   **Conceptual Instantiation**:
    ```kotlin
    // import dev.helight.krescent.mongo.MongoEventStore
    // import com.mongodb.kotlin.client.coroutine.MongoClient // Make sure to use the Kotlin driver

    // // 1. Create a MongoDB Client
    // val mongoClient = MongoClient.create("mongodb://localhost:27017") // Your MongoDB connection string

    // // 2. Get a reference to your database
    // val database = mongoClient.getDatabase("my_event_store_db")

    // // 3. Instantiate MongoEventStore with the events collection
    // // The collection will be created if it doesn't exist.
    // val mongoEventStore = MongoEventStore(database.getCollection("events"))
    ```
    Ensure you handle the `mongoClient` lifecycle appropriately in your application.

### `krescent-kurrent` (Kurrent)

-   **Use Cases**: For applications choosing Kurrent as their specialized event store.
-   **Functionality**: Provides `KurrentEventStore` which implements `StreamingEventSource` and `EventPublisher`.
-   **Setup**: Requires a running Kurrent instance and the `krescent-kurrent` dependency.
    ```gradle
    // build.gradle.kts
    dependencies {
        implementation("dev.helight.krescent:krescent-kurrent:LATEST_VERSION")
    }
    ```
-   **Conceptual Instantiation**:
    ```kotlin
    // import dev.helight.krescent.kurrent.KurrentEventStore
    // import dev.helight.kurrent.KurrentClient

    // // 1. Create and configure a KurrentClient
    // val kurrentClient = KurrentClient.create {
    //     // Add your Kurrent specific configuration here
    //     // e.g., endpoints, credentials, etc.
    // }

    // // 2. Get a Kurrent Stream
    // val kurrentStream = kurrentClient.stream("myStreamName") // Or kurrentClient.topic("myTopic")

    // // 3. Instantiate KurrentEventStore
    // val kurrentEventStore = KurrentEventStore(kurrentStream)
    ```
    Refer to the Kurrent documentation for `KurrentClient` configuration details.

## Publishing Events with `EventPublisher`

The `EventPublisher` interface is straightforward, primarily offering methods to publish one or more event messages.

-   **`publish(eventMessage: EventMessage)`**: Publishes a single `EventMessage` to the event store.
-   **`publishAll(eventMessages: List<EventMessage>)`**: Publishes a list of `EventMessage` objects, often in a transactional manner if the underlying store supports it.

Remember that you don't publish your domain `Event` objects directly. Instead, you use an `EventCatalog` to convert your typed `Event` into an `EventMessage`. The `EventMessage` contains the serialized event payload and metadata like the event type name.

```kotlin
import dev.helight.krescent.event.Event
import dev.helight.krescent.event.buildEventCatalog
import dev.helight.krescent.source.EventPublisher // Assuming you have a publisher instance
import kotlinx.serialization.Serializable

@Serializable
data class MySimpleEvent(val message: String) : Event()

val catalog = buildEventCatalog(1) {
    event<MySimpleEvent>("mySimpleEvent")
}

// Assume 'eventPublisher' is an instance of an EventPublisher (e.g., InMemoryEventStore, MongoEventStore)
// suspend fun examplePublish(eventPublisher: EventPublisher) {
//     // 1. Create your domain event
//     val myEvent = MySimpleEvent("Hello Event Sourcing!")
//
//     // 2. Convert it to an EventMessage using the catalog
//     val eventMessage = catalog.create(myEvent)
//
//     // 3. Publish the EventMessage
//     eventPublisher.publish(eventMessage)
//     println("Event published!")
// }
```

## Choosing an Event Source

When selecting an event source implementation, consider the following factors:

-   **Persistence Needs**: Do events need to survive application restarts? If so, `InMemoryEventStore` is not suitable for production.
-   **Scalability**: How many events do you anticipate? How many concurrent readers/writers? Systems like MongoDB or Kurrent are designed for higher scale than in-memory solutions.
-   **Existing Infrastructure**: If you already use MongoDB, `krescent-mongo` might be a natural fit.
-   **Complexity**: `InMemoryEventStore` is the simplest to set up. Persistent stores require managing the external database system.
-   **Transactional Guarantees**: Understand the transactional capabilities of the chosen event store, especially for publishing multiple events.

Start simple, perhaps with `InMemoryEventStore` for development, and then migrate to a persistent solution as your application matures and its requirements become clearer.
