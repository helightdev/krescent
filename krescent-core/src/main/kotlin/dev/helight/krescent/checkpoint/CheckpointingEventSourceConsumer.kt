package dev.helight.krescent.checkpoint

import dev.helight.krescent.event.*
import dev.helight.krescent.source.EventSourceConsumer
import dev.helight.krescent.source.EventSourcingStrategy
import dev.helight.krescent.source.StreamingEventSource
import dev.helight.krescent.source.StreamingToken
import kotlinx.datetime.Clock

class CheckpointingEventSourceConsumer(
    val namespace: String,
    val version: String,
    val checkpointStrategy: CheckpointStrategy,
    val source: StreamingEventSource,
    val checkpointStorage: CheckpointStorage,
    val checkpointSupports: List<CheckpointSupport>,
    val consumer: EventMessageStreamProcessor,
) : EventSourceConsumer {

    @Suppress("UNCHECKED_CAST")
    override suspend fun strategy(strategy: EventSourcingStrategy) {
        var lastCheckpoint = loadLastCheckpoint()
        var lastPosition: StreamingToken<*>? = lastCheckpoint?.position?.let {
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
                    val checkpoint = checkpoint(position)
                    checkpointStorage.storeCheckpoint(checkpoint)
                    lastCheckpoint = checkpoint
                }
                lastPosition = position
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
        if (lastCheckpoint != null && lastCheckpoint.version != version) {
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

    private suspend fun checkpoint(position: StreamingToken<*>): StoredCheckpoint {
        val bucket = CheckpointBucket(mutableMapOf())
        checkpointSupports.forEach { it.createCheckpoint(bucket) }
        return StoredCheckpoint(
            namespace = namespace,
            version = version,
            position = position.serialize(),
            timestamp = Clock.System.now(),
            data = bucket
        )
    }

    private suspend fun load(storedCheckpoint: StoredCheckpoint) {
        checkpointSupports.forEach { it.restoreCheckpoint(storedCheckpoint.data) }
    }
}

