---
title: Event Stream Processing
---

Event stream processing in Krescent involves the continuous handling and transformation of sequences of events.

## `EventStreamProcessor` Interface

The core of event stream processing in Krescent is the `EventStreamProcessor` interface. This interface defines the fundamental contract for components that consume and process streams of events. Implementations of this interface are responsible for handling events as they arrive, performing computations, and potentially generating new events.

## `EventMessageStreamProcessor`

For processing raw event messages before they are deserialized into typed events, Krescent provides the `EventMessageStreamProcessor`. This processor operates on `EventMessage` objects, allowing for early-stage filtering, routing, or transformation based on message metadata or payload structure before the full deserialization cost is incurred.

## `TransformingModelEventProcessor`

The `TransformingModelEventProcessor` is a key component that leverages the `EventCatalog`. Its primary function is to:

1.  **Deserialize**: Take an incoming `EventMessage` and use the `EventCatalog` to deserialize its payload into a specific, typed `Event` object.
2.  **Broadcast**: After successful deserialization, it broadcasts the typed `Event` to other interested downstream processors.

This processor acts as a bridge, converting raw event data into meaningful, domain-specific event objects.

## `BroadcastEventStreamProcessor`

The `BroadcastEventStreamProcessor` serves to distribute events to multiple downstream `EventStreamProcessor` instances. When an event is received by the `BroadcastEventStreamProcessor`, it forwards the event to all registered downstream processors, enabling parallel processing or fan-out scenarios. This is useful when multiple independent components need to react to the same event.
