---
title: Checkpointing
---

Checkpointing in Krescent is a crucial optimization mechanism for event models. Its primary purpose is to periodically
save the current state of an event model, along with the position (e.g., `StreamingToken`) of the last event processed
to reach that state. This avoids the need to reprocess the entire event stream from the beginning every time the model
is initialized or restarted.

## `CheckpointStrategy`

A `CheckpointStrategy` determines *when* a checkpoint should be taken. Krescent offers several built-in strategies:

- **`FixedEventRateCheckpointStrategy`**: Triggers a checkpoint after a fixed number of events have been processed. For
  example, checkpoint every 1000 events.
- **`FixedTimeRateCheckpointStrategy`**: Triggers a checkpoint after a fixed amount of time has elapsed since the last
  checkpoint. For example, checkpoint every 5 minutes.
- **`AlwaysCheckpointStrategy`**: Trigger a checkpoint after every event processed. This is either useful if creating
  checkpoints is very inexpensive or the projection of a read model represents the latest snapshot with the read model
  being able to handle transactions and rollbacks.
- **`ManualCheckpointStrategy`**: Allows the application to trigger checkpoints programmatically based on custom logic
  or external signals. This provides the most flexibility but requires explicit management.

## `CheckpointStorage`

The `CheckpointStorage` interface defines *how* and *where* checkpoints are saved and loaded. Implementations of this
interface are responsible for serializing the model's state and the associated `StreamingToken`, persisting them to a
durable store (like a file system, database, or cloud storage), and retrieving them when needed.

## `CheckpointSupport`

`CheckpointSupport` is an interface that must be implemented by components (typically event models or parts of them)
that can be checkpointed.