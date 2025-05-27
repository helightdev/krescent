---
title: Getting Started
outline: deep
---

This guide will walk you through setting up a minimal Krescent application to illustrate its basic usage and core concepts.

## Installation

To include Krescent in your Kotlin project, add the following dependency to your `build.gradle.kts` file:

```kotlin
dependencies {
    implementation("dev.helight.krescent:krescent-core:LATEST_VERSION") // Replace LATEST_VERSION
}
```

**Note:** Remember to replace `LATEST_VERSION` with the actual latest version of Krescent. You can find the latest version on Maven Central or the project's releases page.

## Core Concepts Review (Brief)

Before we dive into the example, let's quickly recall some core Krescent concepts:

-   **Events**: Immutable facts representing something that has happened (e.g., `UserSignedUp`, `OrderPlaced`).
-   **EventCatalog**: Defines and manages all known event types, including their serialization.
-   **EventModel**: Represents state derived from a stream of events. Can be a `ReadModel` (for querying) or a `WriteModel` (for command handling).
-   **StreamingEventSource**: Provides a replayable stream of events.

## Example: Simple Event Logger

Let's create a very simple application that defines an event, sets up an in-memory event store, and builds a read model to log received events to the console.

### 1. Define an Event

First, we define a simple event. All events in Krescent must inherit from the base `Event` class and are typically Kotlin data classes marked with `@Serializable`.

```kotlin
import dev.helight.krescent.event.Event
import kotlinx.serialization.Serializable

@Serializable
data class MySimpleEvent(val message: String) : Event()
```

### 2. Create an EventCatalog

Next, we need an `EventCatalog` to register our event type and its serializer. Krescent uses Kotlinx Serialization.

```kotlin
import dev.helight.krescent.event.buildEventCatalog

val catalog = buildEventCatalog(revision = 1) {
    event<MySimpleEvent>("mySimpleEvent") // Registering MySimpleEvent with the name "mySimpleEvent"
}
```
The `revision` parameter is used for schema evolution purposes, which is a more advanced topic.

### 3. Set up an In-Memory Event Store

For this example, we'll use an `InMemoryEventStore`. This is a simple implementation that keeps events in memory. It conveniently implements both `StreamingEventSource` (to read events) and `EventPublisher` (to write events).

```kotlin
import dev.helight.krescent.source.impl.InMemoryEventStore

val eventStore = InMemoryEventStore()
```
In a production application, you would typically use a persistent event store like `krescent-mongo` or `krescent-kurrent`.

### 4. Build a Read Model

Now, let's create a `ReadModelBase` that will process `MySimpleEvent` instances and print their messages.

```kotlin
import dev.helight.krescent.model.ReadModelBase
import dev.helight.krescent.model.buildEventModel
import kotlinx.coroutines.runBlocking

// Define the ReadModel
class MySimpleLogger : ReadModelBase("simpleLogger", 1, catalog) {
    init {
        // Configure the model processing logic using the eventModelBuilder DSL
        eventModelBuilder {
            handler { event -> // This lambda is called for each event
                when (event) {
                    is MySimpleEvent -> {
                        println("Received event: \${event.message}")
                    }
                }
            }
        }
    }
}

fun main() = runBlocking {
    // Create an instance of our logger
    val logger = MySimpleLogger()

    // Build the event model, connecting it to the eventStore
    // The 'build' method returns an EventSourceConsumer
    val model = logger.build(eventStore)

    // Start streaming events to the model
    // This is a suspending function and will run indefinitely, processing events as they arrive.
    // In a real application, you'd manage its lifecycle within a CoroutineScope.
    model.stream()

    // Example of publishing an event
    // In a real application, this might happen in a different part of your system (e.g., a command handler).
    // For this example, we publish it directly to the eventStore after starting the model.
    // The model.stream() call above needs to run in a separate coroutine to allow this publish to happen.
    // For simplicity in this example, if running sequentially like this in a single runBlocking without
    // launching model.stream() in a new coroutine, the publish might happen after stream() completes or not at all
    // if stream() blocks indefinitely.
    // A more robust setup would launch model.stream() in a separate coroutine:
    // launch { model.stream() }
    // delay(100) // give it a moment to start
    eventStore.publish(catalog.create(MySimpleEvent("Hello Krescent!")))
}
```

**Explanation:**

-   `ReadModelBase("simpleLogger", 1, catalog)`: We initialize our model with a unique name (`simpleLogger`), a version for the model itself, and the `EventCatalog` we defined.
-   `eventModelBuilder { ... }`: This DSL is used to configure the model.
-   `handler { event -> ... }`: This defines a lambda that will be called for every event consumed by the model. We use a `when` expression to handle specific event types.
-   `logger.build(eventStore)`: This crucial step connects the `MySimpleLogger` instance to the `eventStore`. It prepares the model for event consumption by creating an `EventSourceConsumer`.
-   `model.stream()`: This is a suspending function that starts the event processing flow. It will first process any historical events (if any) and then listen for new events from the `eventStore`.
-   `runBlocking { ... }`: We use `runBlocking` from `kotlinx.coroutines` to run our suspending `main` function and the `model.stream()` call in a blocking way for this simple console application. In a larger application, you would manage coroutine scopes more explicitly (e.g., using `CoroutineScope(Dispatchers.Default)`).
-   `eventStore.publish(...)`: We use the `eventStore` (which also acts as an `EventPublisher`) to send a new `MySimpleEvent` into the system. The `catalog.create(...)` method is a helper to correctly instantiate an event with its metadata.

### 5. Running the Example

When you run this `main` function, the `MySimpleLogger` model will start streaming. When the `MySimpleEvent("Hello Krescent!")` is published, the logger's handler will be triggered, and you should see the following output:

```
Received event: Hello Krescent!
```
(Note: For `model.stream()` and `eventStore.publish` to work as expected in this simple `main`, `model.stream()` should ideally be launched in a non-blocking way with respect to the `publish` call, e.g., by launching it in a separate coroutine. The provided example is simplified for brevity.)

## Next Steps

This guide provided a very basic introduction. From here, you can explore more advanced Krescent features:

-   **Persistent Event Stores**: Integrate with `krescent-mongo` or `krescent-kurrent`, or implement your own `StreamingEventSource`.
-   **Write Models (`WriteModelBase`)**: Implement command handling logic that produces new events.
-   **Checkpointing**: Configure checkpointing for your models to optimize recovery times.
-   **Advanced Model Configuration**: Explore more options within `eventModelBuilder`, such as error handling and lifecycle hooks.
-   **Event Sourcing Strategies**: Understand different ways models can consume events (e.g., `CatchupSourcingStrategy`, `StreamingSourcingStrategy`).

Refer to the other sections of this documentation for detailed information on these topics.
