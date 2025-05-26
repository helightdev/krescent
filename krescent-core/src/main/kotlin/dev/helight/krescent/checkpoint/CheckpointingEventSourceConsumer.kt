package dev.helight.krescent.checkpoint

import dev.helight.krescent.event.*
import dev.helight.krescent.source.EventSourceConsumer
import dev.helight.krescent.source.EventSourcingStrategy
import dev.helight.krescent.source.StreamingEventSource
import dev.helight.krescent.source.StreamingToken
import kotlinx.serialization.json.jsonObject
import java.time.Instant

class CheckpointingEventSourceConsumer<T : StreamingToken<T>>(
    val namespace: String,
    val revision: Int,
    val checkpointStrategy: CheckpointStrategy,
    val source: StreamingEventSource<T>,
    val checkpointStorage: CheckpointStorage,
    val additionalCheckpoints: List<CheckpointSupport>,
    val consumer: EventMessageStreamProcessor,
) : EventSourceConsumer<T> {

//    override suspend fun stream() {
//        val lastCheckpoint = loadLastCheckpoint()
//        val flow = source.streamEvents(lastCheckpoint?.position?.let {
//            source.deserializeToken(it)
//        })
//        handlerLoop(lastCheckpoint, flow)
//    }
//
//    override suspend fun catchup() {
//        val lastCheckpoint = loadLastCheckpoint()
//        val flow = source.fetchEventsAfter(lastCheckpoint?.position?.let {
//            source.deserializeToken(it)
//        })
//        handlerLoop(lastCheckpoint, flow)
//        consumer.forwardSystemEvent(SystemStreamTailEvent)
//    }
//
//    override suspend fun restore() {
//        val lastCheckpoint = loadLastCheckpoint()
//        handlerLoop(lastCheckpoint, emptyFlow())
//        consumer.forwardSystemEvent(SystemStreamTailEvent)
//    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun strategy(strategy: EventSourcingStrategy<T>) {
        var lastCheckpoint = loadLastCheckpoint()
        var lastPosition: T? = lastCheckpoint?.position?.let {
            source.deserializeToken(it)
        }
        strategy.source(source, lastPosition, object : EventMessageStreamProcessor {
            override suspend fun process(
                message: EventMessage,
                position: StreamingToken<*>,
            ) {
                consumer.process(message, position)
                val tickerResult = checkpointStrategy.tick(message, lastCheckpoint)
                if (tickerResult) {
                    val checkpoint = checkpoint(position as T)
                    checkpointStorage.storeCheckpoint(checkpoint)
                    lastCheckpoint = checkpoint
                }
                lastPosition = position as T
            }

            override suspend fun forwardSystemEvent(event: Event) {
                consumer.forwardSystemEvent(event)
            }
        })

        if (checkpointStrategy.tickGracefulTermination() && lastPosition != null) {
            val checkpoint = checkpoint(lastPosition)
            checkpointStorage.storeCheckpoint(checkpoint)
        }
    }

    private suspend fun loadLastCheckpoint(): StoredCheckpoint? {
        var lastCheckpoint = checkpointStorage.getLatestCheckpoint(namespace)

        // Invalidate outdated checkpoints
        if (lastCheckpoint != null && lastCheckpoint.revision != revision) {
            lastCheckpoint = null
        }

        if (lastCheckpoint != null) {
            load(lastCheckpoint)
            consumer.forwardSystemEvent(SystemStreamRestoredEvent)
        } else {
            consumer.forwardSystemEvent(SystemStreamHeadEvent)
        }
        return lastCheckpoint
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

