---
title: Event Sourcing Strategies
---

An `EventSourcingStrategy` in Krescent defines the specific approach for how events are fetched from a `StreamingEventSource` and subsequently processed by an `EventModel`. It dictates the lifecycle of event consumption for a model.

Krescent provides several common strategies to suit different use cases:

-   **`CatchupSourcingStrategy`**: This strategy is designed to process all available historical events from the `StreamingEventSource` up to the current tail of the stream. Once all historical events have been fetched and processed, the strategy stops. This is typically used for building projections or read models that only need a snapshot of the current state based on past events.

-   **`StreamingSourcingStrategy`**: This strategy first fetches all historical events, similar to `CatchupSourcingStrategy`. However, after processing the historical events, it continues to listen for and process new events as they arrive in the `StreamingEventSource`. This is suitable for live, continuously updating models that need to react to new information in real-time.

-   **`NoSourcingStrategy`**: This strategy is used when an `EventModel` should not process any events from the source. Instead, it's typically used to restore a model to its initial empty state or to a previously saved checkpointed state without replaying events. This can be useful for initializing models or for scenarios where state is managed externally.

## `WriteCompatibleEventSourcingStrategy`

A `WriteCompatibleEventSourcingStrategy` is a marker interface indicating that the strategy is compatible with `WriteModelBase` instances. These strategies are suitable for models that not only consume events but also produce new events as a result of command processing. Both `CatchupSourcingStrategy` and `StreamingSourcingStrategy` can be write-compatible, allowing write models to build their state and then process commands that generate further events.
