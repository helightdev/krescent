package dev.helight.krescent.checkpoints

import dev.helight.krescent.EventMessage
import dev.helight.krescent.EventMessageStreamProcessor
import dev.helight.krescent.StreamingEventSource
import dev.helight.krescent.StreamingToken
import dev.helight.krescent.event.EventSourceConsumer
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.jsonObject
import java.time.Instant

class CheckpointingEventSourceConsumer<T: StreamingToken<T>> (
    val namespace: String,
    val revision: Long,
    val strategy: CheckpointStrategy,
    val source: StreamingEventSource<T>,
    val checkpointStorage: CheckpointStorage,
    val consumer: EventMessageStreamProcessor
) : EventSourceConsumer {

    override suspend fun stream() {
        val lastCheckpoint = loadLastCheckpoint()
        val flow = source.streamEvents(lastCheckpoint?.position?.let {
            source.deserializeToken(it)
        })
        handlerLoop(lastCheckpoint, flow)
    }

    override suspend fun catchup() {
        val lastCheckpoint = loadLastCheckpoint()
        val flow = source.fetchEventsAfter(lastCheckpoint?.position?.let {
            source.deserializeToken(it)
        })
        handlerLoop(lastCheckpoint, flow)
    }

    private suspend fun loadLastCheckpoint(): StoredCheckpoint? {
        var lastCheckpoint = checkpointStorage.getLatestCheckpoint(namespace)

        // Invalidate outdated checkpoints
        if (lastCheckpoint != null && lastCheckpoint.revision != revision) {
            lastCheckpoint = null
        }

        if (lastCheckpoint != null) {
            load(lastCheckpoint)
        }
        return lastCheckpoint
    }

    private suspend fun handlerLoop(checkpoint: StoredCheckpoint?, flow: Flow<Pair<EventMessage, T>>) {
        var lastCheckpoint = checkpoint
        flow.collect {
            val (message, position) = it
            consumer.process(message, position)
            val tickerResult = strategy.tick(message, lastCheckpoint)
            if (tickerResult) {
                val checkpoint = checkpoint(position)
                checkpointStorage.storeCheckpoint(checkpoint)
                lastCheckpoint = checkpoint
            }
        }
    }

    private suspend fun checkpoint(position: T): StoredCheckpoint {
        val bucket = CheckpointBucket(mutableMapOf())
        CheckpointSupport.storagePass(consumer, bucket)
        return StoredCheckpoint(
            namespace = namespace,
            revision = revision,
            position = position.serialize(),
            timestamp = Instant.now(),
            data = bucket.buildJsonObject()
        )
    }

    private suspend fun load(storedCheckpoint: StoredCheckpoint) {
        val bucket = CheckpointBucket(storedCheckpoint.data.jsonObject.toMutableMap())
        CheckpointSupport.restorePass(consumer, bucket)
    }

}