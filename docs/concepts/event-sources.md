---
title: Event Sources
---

Event Sources in Krescent are responsible for providing a replayable stream of events. They are fundamental to how event models reconstruct their state and how new events are introduced into the system.

## `StreamingEventSource` Interface

The primary interface for consuming events is `StreamingEventSource`. It defines the following key methods:

- **`getHeadToken()`**: Returns a `StreamingToken` representing the beginning of the event stream.
- **`getTailToken()`**: Returns a `StreamingToken` representing the current end (latest event) of the event stream.
- **`fetchEventsAfter(token: StreamingToken, limit: Int)`**: Fetches a batch of events that occurred after the given `StreamingToken`, up to a specified `limit`.
- **`streamEvents(token: StreamingToken, scope: CoroutineScope)`**: Returns a `Flow` of events that occur after the given `StreamingToken`. This allows for continuous consumption of new events as they arrive.

A `StreamingToken` is an opaque marker that represents a specific position within the event stream, enabling consumers to resume event processing from a known point.

## `ExtendedQueryableStreamingEventSource` Interface

For more advanced querying capabilities, Krescent provides the `ExtendedQueryableStreamingEventSource` interface. It extends `StreamingEventSource` and adds methods such as:

- Querying events by a specific time range.
- Fetching a specific event by its unique ID.

These capabilities are useful for scenarios requiring more targeted event retrieval beyond simple sequential streaming.

## `EventPublisher` Interface

Complementary to sourcing events, the `EventPublisher` interface is responsible for publishing new events into an event source. It typically provides methods to publish one or more events, which are then persisted and become available for consumption by `StreamingEventSource` implementations.
