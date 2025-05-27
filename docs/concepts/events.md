---
title: Events
---

Events in Krescent are immutable facts that represent something that has happened in the system. They are the primary
building blocks of Krescent's event-driven architecture.

## Physical vs Virtual Events

In Krescent, events can be categorized into three types:

- **Physical Events**: These are events that are sourced from an event stream and have a position, id and timestamp.
  All events in this category must be strictly serializable and are typically defined as Kotlin data classes.
- **Virtual Events**: These are events created by the framework, a virtual event stream or model extensions and
  are either derived from physical events or related to the event processing pipelines state.
- **System Events**: These are just a special case of virtual events which are only emitted by the framework and usually
  represent internal state changes, notifications or stream state changes.

#### Example Event Definition

```kotlin
@Serializable
data class BookAddedEvent(
  override val bookId: String,
  val title: String,
  val author: String,
  val price: Double,
  val copies: Int,
) : Event(), BookEvent

@Serializable
data class BookPriceChangedEvent(
  override val bookId: String,
  val price: Double,
) : Event(), BookEvent

interface BookEvent {
  val bookId: String
}
```

## Event Catalog

Physical events must be registered in one or more `EventCatalog` instances. The `EventCatalog` is responsible for
defining and managing known event types, their serialization and deserialization for the event processing pipeline.

The catalog also defines the type identifier for each event. Naming usually follows domain-based `domain.event`
naming conventions where the type starts with the namespace of the domain and then ends with the event name.

```kotlin
val bookstoreEventCatalog = buildEventCatalog(1) {
  event<BookAddedEvent>("book.added")
  event<BookPriceChangedEvent>("book.price_changed")
  event<BookRemovedEvent>("book.removed")
  event<BookLentEvent>("book.lent")
  event<BookReturnedEvent>("book.returned")
  event<BookCopyAddedEvent>("book.copy_added")
  event<BookCopyRemovedEvent>("book.copy_removed")
}
```

All event catalogs are **versioned** using a revision number. You should increment the revision number **whenever
breaking changes** are made to the events in the catalog that would **affect serialization or deserialization** or
**change the meaning** of the events.

> [!IMPORTANT]
> A change in this revision number will cause all models that use this catalog to discard their checkpoints the next
> time they are loaded.

