---
title: Event Catalog
---

The `EventCatalog` in Krescent is responsible for defining and managing all known event types within the system, as well as their serialization and deserialization.

## `EventCatalogBuilder`

Event types are registered with the `EventCatalog` using the `EventCatalogBuilder`. When registering an event, you provide:

- **Name**: A unique string identifier for the event type.
- **Serializer**: The serializer responsible for converting the event object to and from a persistent format.

Krescent leverages Kotlinx Serialization for its robust and efficient serialization capabilities.

## Encoding and Decoding

The `EventCatalog` plays a crucial role in the event processing pipeline:

- **Encoding**: It transforms typed `Event` objects into `EventMessage` objects. An `EventMessage` is a generic container that typically includes the event's type name and its serialized payload.
- **Decoding**: Conversely, it takes an `EventMessage` and, using the registered event type name and serializer, decodes the payload back into the specific, typed `Event` object.

This mechanism allows Krescent to handle diverse event types in a flexible and type-safe manner.
