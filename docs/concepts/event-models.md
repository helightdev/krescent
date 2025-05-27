---
title: Event Models
---

An `EventModel` in Krescent represents a state that is derived from a stream of events. It is a core concept for building applications that react to and are driven by events. Each `EventModel` consumes events through an `EventSourceConsumer`, which is responsible for feeding events from an `StreamingEventSource` to the model according to a chosen `EventSourcingStrategy`.

## `EventModelBuilder` DSL

Krescent provides a powerful Domain-Specific Language (DSL) through the `EventModelBuilder` for constructing and configuring event models. This builder allows you to declaratively define:

-   **Event Handlers**: Functions that specify how the model's state changes in response to different types of events.
-   **Extensions**: Custom functionalities that can be added to the model.
-   **Checkpointing**: Strategies and storage mechanisms for saving and restoring the model's state.
-   **Initial State**: The starting state of the model before any events are processed.

## Types of Event Models

Krescent distinguishes between two primary types of event models based on their purpose:

### `ReadModelBase`

`ReadModelBase` is designed for creating projections, queries, and read-only views of data. These models consume events to build up their state, which can then be queried by other parts of the application. They are optimized for efficient read access and do not publish new events.

### `WriteModelBase`

`WriteModelBase` is used for command handling and business logic that can result in the creation of new events. Like read models, they consume events to build their internal state. However, they also expose methods to:

-   **`emitEvent(event: Event)`**: Tentatively stages a new event to be published. This event is not yet persisted or made available to other consumers.
-   **`commitEvents()`**: Persists all staged events (those added via `emitEvent`) to the `EventPublisher`, making them part of the event stream and available for other models and consumers.

`WriteModelBase` instances are central to implementing the command side of a CQRS (Command Query Responsibility Segregation) architecture.

## `ModelExtension`

`ModelExtension` provides a way to add custom, reusable functionality to event models. Extensions can hook into the model's lifecycle, access its state, and provide additional methods or behaviors. This allows for a clean separation of concerns and promotes modularity in model design. Examples could include logging, metrics collection, or specialized state management utilities.
