package dev.helight.krescent.checkpoint

import dev.helight.krescent.event.EventMessage
import dev.helight.krescent.event.EventMessageStreamProcessor
import dev.helight.krescent.event.SystemStreamHeadEvent
import dev.helight.krescent.event.SystemStreamRestoredEvent
import dev.helight.krescent.source.EventSourceConsumer
import dev.helight.krescent.source.StreamingEventSource
import dev.helight.krescent.source.StreamingToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.json.jsonObject
import java.time.Instant

class CheckpointingEventSourceConsumer<T : StreamingToken<T>>(
    val namespace: String,
    val revision: Int,
    val strategy: CheckpointStrategy,
    val source: StreamingEventSource<T>,
    val checkpointStorage: CheckpointStorage,
    val additionalCheckpoints: List<CheckpointSupport>,
    val consumer: EventMessageStreamProcessor,
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

    override suspend fun restore() {
        val lastCheckpoint = loadLastCheckpoint()
        handlerLoop(lastCheckpoint, emptyFlow())

    }

    private suspend fun loadLastCheckpoint(): StoredCheckpoint? {
        var lastCheckpoint = checkpointStorage.getLatestCheckpoint(namespace)

        // Invalidate outdated checkpoints
        if (lastCheckpoint != null && lastCheckpoint.revision != revision) {
            lastCheckpoint = null
        }

        if (lastCheckpoint != null) {
            load(lastCheckpoint)
            consumer.forwardSystemEvent(SystemStreamRestoredEvent())
        } else {
            consumer.forwardSystemEvent(SystemStreamHeadEvent())
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
        additionalCheckpoints.forEach { it.createCheckpoint(bucket) }
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
        additionalCheckpoints.forEach { it.restoreCheckpoint(bucket) }
        CheckpointSupport.restorePass(consumer, bucket)
    }

}