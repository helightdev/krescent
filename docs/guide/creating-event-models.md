---
title: Creating Event Models
outline: deep
---

Event Models are central to Krescent's architecture. They process streams of events to build and maintain state, whether for answering queries (read models) or for handling commands and business logic (write models). The primary way to configure these models is through the `EventModelBuilder` DSL.

## Building Event Models with `EventModelBuilder`

You typically define an event model by creating a class that inherits from `ReadModelBase` or `WriteModelBase`. Both of these base classes utilize the `EventModelBuilder` internally for their configuration. Alternatively, for more direct control, you can use methods like `StreamingEventSource.buildEventModel(...)` if you are not using the provided base classes, though using the base classes is the common approach.

The `EventModelBuilder` provides a declarative way to set up your model's behavior.

### Handler

The core of an event model is its event handling logic. This is defined using the `handler` block within the `eventModelBuilder`. This lambda function is called for each event consumed by the model.

```kotlin
// Inside your EventModelBase subclass constructor or init block:
eventModelBuilder {
    handler { event -> // This lambda is called for each event
        when (event) {
            is MyEventA -> {
                // Process MyEventA
            }
            is MyEventB -> {
                // Process MyEventB
            }
            // ... other event types
        }
    }
}
```

### Extensions

`ModelExtension` allows you to add reusable functionalities or encapsulate complex logic that can be shared across multiple models. For example, you might have extensions for logging, metrics, or specialized state manipulation.

```kotlin
// Inside your EventModelBase subclass constructor or init block:
eventModelBuilder {
    // ... other configurations ...
    // install(MyCustomExtension()) // Example of installing an extension
}
```
Detailed guides on creating and using specific extensions or advanced extension patterns will be covered in an "Advanced" section or dedicated extension guides.

### Checkpointing

Checkpointing saves the model's state periodically to avoid reprocessing all events from scratch on restart. You configure it using `useCheckpoints`.

```kotlin
// Inside your EventModelBase subclass constructor or init block:
// Assuming 'myCheckpointStorage' and 'myCheckpointStrategy' are defined
// eventModelBuilder {
//     useCheckpoints(myCheckpointStorage, myCheckpointStrategy)
// }
```
For detailed information on available strategies (like `FixedEventRateCheckpointStrategy`) and how to implement `CheckpointStorage`, please refer to the [Checkpointing concept page](/concepts/checkpointing.md).

## Read Models (`ReadModelBase`)

Read models are used for creating projections of data, answering queries, or performing any side-effect-free event processing. They consume events to build up a state that can be queried by other parts of your application (e.g., an API endpoint, a UI).

Here's an example of a simple `ReadModelBase` implementation that builds a projection of user data:

```kotlin
import dev.helight.krescent.event.Event
import dev.helight.krescent.event.EventCatalog
import dev.helight.krescent.event.buildEventCatalog // Added import
import dev.helight.krescent.model.ReadModelBase
import dev.helight.krescent.model.eventModelBuilder
import dev.helight.krescent.source.StreamingEventSource
import kotlinx.serialization.Serializable

@Serializable
data class MyEvent(val data: String) : Event()

// Assume catalog is defined, e.g., reusing from getting-started or defined here
val catalog = buildEventCatalog(1) { event<MyEvent>("myEvent") }

class UserProjection(
    private val db: MutableMap<String, String> // Simplified data store for the projection
) : ReadModelBase("userProjection", 1, catalog) { // Model name, version, and catalog
    init {
        eventModelBuilder {
            handler { event ->
                when (event) {
                    is MyEvent -> {
                        // Example: event.data might be "userId123:Alice"
                        val parts = event.data.split(":", limit = 2)
                        if (parts.size == 2) {
                            val id = parts[0]
                            val value = parts[1]
                            db[id] = value
                            println("UserProjection updated: \$id -> \$value")
                        }
                    }
                }
            }
        }
    }

    // Method to query the projection's state
    fun getUserData(id: String): String? = db[id]
}

// Example Usage (conceptual):
// val eventStore = InMemoryEventStore() // Assuming an event source
// val userDb = mutableMapOf<String, String>()
// val projection = UserProjection(userDb)
//
// // Connect the model to the event source and start processing
// val modelConsumer = projection.build(eventStore) // Returns an EventSourceConsumer
//
// // To process historical events and then stop:
// // modelConsumer.catchup()
//
// // To process historical events and then listen for new ones (needs a coroutine scope):
// // GlobalScope.launch { modelConsumer.stream() }
//
// // ... later, after events have been published and processed ...
// // val userData = projection.getUserData("userId123")
// // println("Fetched user data: \$userData")
```
In this example, the `UserProjection` maintains a simple `MutableMap` as its state. The `handler` updates this map based on incoming `MyEvent` instances. The `getUserData` method allows other components to query this state.

## Write Models (`WriteModelBase`)

Write models are responsible for handling commands, validating business rules, and, as a result, publishing new events. They are the command-processing side of a CQRS architecture.

Key methods for `WriteModelBase`:

-   `emitEvent(event: Event)`: Stages an event to be published. This event is not immediately persisted but is queued within the model.
-   `commitEvents()`: Manually triggers the publication of all staged events to the `EventPublisher`. Often, this is handled automatically by the chosen `EventSourcingStrategy` (e.g., after a batch of events from the source stream has been processed, or when a `SystemHintCommitTransactionEvent` is encountered).

`WriteModelBase` requires an `EventPublisher` to send out new events. Often, the same component that acts as the `StreamingEventSource` (like `InMemoryEventStore` or `MongoEventStore`) also implements `EventPublisher`.

Here's an example of a `WriteModelBase` that handles a command to create a new user:

```kotlin
import dev.helight.krescent.event.Event
import dev.helight.krescent.event.EventCatalog
import dev.helight.krescent.event.buildEventCatalog // Added import
import dev.helight.krescent.model.WriteModelBase
import dev.helight.krescent.model.eventModelBuilder
import dev.helight.krescent.source.EventPublisher
import dev.helight.krescent.source.StreamingEventSource
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable

@Serializable
data class UserCreatedEvent(val userId: String, val name: String) : Event()

// Extend or redefine catalog to include UserCreatedEvent
val writeModelCatalog = buildEventCatalog(1) {
    event<UserCreatedEvent>("userCreated")
    // Potentially other events the write model might react to or produce
}

class UserCommands(
    source: StreamingEventSource, // For initial state hydration if needed
    publisher: EventPublisher    // To publish new events
) : WriteModelBase("userCommands", 1, writeModelCatalog, source, publisher) {

    // Internal state, e.g., to ensure username uniqueness
    private val existingUsernames = mutableSetOf<String>()

    init {
        eventModelBuilder {
            handler { event ->
                // Write models can also build their internal state from the event stream
                if (event is UserCreatedEvent) {
                    existingUsernames.add(event.name)
                    println("UserCommands (state hydration): User '\${event.name}' added to existing usernames.")
                }
            }
        }
    }

    // Command handler method
    suspend fun handleCreateUserCommand(userId: String, name: String) {
        if (existingUsernames.contains(name)) {
            println("Command Rejected: Username '\$name' already exists.")
            return // Or throw a custom domain exception
        }

        val event = UserCreatedEvent(userId, name)
        emitEvent(event) // Stage the event for publishing

        // Optimistic state update: Assume the event will be successfully persisted.
        // This is a common pattern, but requires careful consideration of failure scenarios.
        existingUsernames.add(name)
        println("Emitted UserCreatedEvent for \$name. It's now staged for commit.")
        // The actual commit to the publisher often happens based on the sourcing strategy
        // or an explicit call to commitEvents() if not handled by the strategy.
    }
}

// Example Usage (conceptual):
// val eventStore = InMemoryEventStore() // Implements both StreamingEventSource and EventPublisher
// val userCommands = UserCommands(eventStore, eventStore)
//
// // Connect the model to the event source and start processing
// val modelConsumer = userCommands.build(eventStore)
//
// // Start streaming (for state hydration and for strategies that trigger commits)
// // GlobalScope.launch { modelConsumer.stream() }
//
// // runBlocking { // Example command invocation
// //     userCommands.handleCreateUserCommand("user123", "Alice")
// //     userCommands.handleCreateUserCommand("user456", "Bob")
// //     userCommands.handleCreateUserCommand("user789", "Alice") // This one should be rejected
// // }
// // In many setups with InMemoryEventStore, emitted events are published very quickly
// // due to how SystemHintCommitTransactionEvent might be handled by default strategies.
```

**Flow:**
1.  A command (e.g., `handleCreateUserCommand`) is invoked on the write model.
2.  The model performs validation (e.g., checks if username exists).
3.  If validation passes, it creates an event object and calls `emitEvent(event)`.
4.  The event is now staged. The `EventSourcingStrategy` (e.g., `StreamingSourcingStrategy`) often plays a role in deciding when to actually call `commitEvents()`. For instance, some strategies commit after processing a batch of incoming events or upon specific system events.
5.  Once committed, the `EventPublisher` persists the event.

## Choosing Between Read and Write Models

-   **Use `ReadModelBase` when:**
    -   You need to create a queryable projection of data from events.
    -   The model's primary purpose is to provide information (e.g., for UIs, reports).
    -   The processing is side-effect-free (does not generate new events).

-   **Use `WriteModelBase` when:**
    -   You need to handle commands or operations that change the state of the system.
    -   The model needs to enforce business rules and validations.
    -   The outcome of processing a command is the generation of one or more new events.

It's common in CQRS architectures to have many read models tailored for specific query needs and fewer, more focused write models that manage transactional consistency and business logic.
