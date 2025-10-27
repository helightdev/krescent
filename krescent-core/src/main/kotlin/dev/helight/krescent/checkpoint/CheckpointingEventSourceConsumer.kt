package dev.helight.krescent.checkpoint

import dev.helight.krescent.event.*
import dev.helight.krescent.source.EventSourceConsumer
import dev.helight.krescent.source.EventSourcingStrategy
import dev.helight.krescent.source.StreamingEventSource
import dev.helight.krescent.source.StreamingToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import java.util.logging.Logger

class CheckpointingEventSourceConsumer(
    val namespace: String,
    val version: String,
    val checkpointStrategy: CheckpointStrategy,
    val source: StreamingEventSource,
    val checkpointStorage: CheckpointStorage,
    val checkpointSupports: List<CheckpointSupport>,
    val consumer: EventMessageStreamProcessor,
    val rebuildOnInvalidCheckpoint: Boolean = true,
) : EventSourceConsumer {

    private val logger = Logger.getLogger("CheckpointingEventSourceConsumer")

    @Suppress("UNCHECKED_CAST")
    override suspend fun strategy(strategy: EventSourcingStrategy) {
        var lastCheckpoint = loadLastCheckpoint()
        var lastPosition: StreamingToken<*>? = lastCheckpoint?.position?.let {
            source.deserializeToken(it)
        }
        try {
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
                        lastCheckpoint = checkpoint // TODO: Why is not used per lint? This isn't closed and should work
                    }
                    lastPosition = position
                }

                override suspend fun forwardSystemEvent(event: Event) {
                    consumer.forwardSystemEvent(event)
                }
            })
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                if (checkpointStrategy.tickGracefulTermination() && lastPosition != null) {
                    val checkpoint = checkpoint(lastPosition)
                    checkpointStorage.storeCheckpoint(checkpoint)
                }
            }
            throw e
        }

        if (checkpointStrategy.tickGracefulTermination() && lastPosition != null) {
            val checkpoint = checkpoint(lastPosition)
            checkpointStorage.storeCheckpoint(checkpoint)
        }
    }

    private suspend fun loadLastCheckpoint(): StoredCheckpoint? {
        var lastCheckpoint = checkpointStorage.getLatestCheckpoint(namespace)

        // Invalidate outdated checkpoints
        if (lastCheckpoint != null && lastCheckpoint.version != version) {
            logger.info("Checkpoint version mismatch, expected '$version' but found '${lastCheckpoint.version}'")
            lastCheckpoint = null
            if (!rebuildOnInvalidCheckpoint) throw CheckpointValidationException()
        }

        if (lastCheckpoint != null && !validateCheckpoint(lastCheckpoint)) {
            logger.info("Checkpoint validation failed for namespace '$namespace'")
            lastCheckpoint = null
            if (!rebuildOnInvalidCheckpoint) throw CheckpointValidationException()
        }

        if (lastCheckpoint != null) {
            logger.info("Valid checkpoint found for namespace '$namespace', restoring state")
            loadCheckpoint(lastCheckpoint)
            consumer.forwardSystemEvent(SystemStreamRestoredEvent)
        } else {
            logger.info("No valid checkpoint found for namespace '$namespace', starting from scratch")
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

    private suspend fun validateCheckpoint(bucket: StoredCheckpoint): Boolean {
        return checkpointSupports.all { it.validateCheckpoint(bucket.data) }
    }

    private suspend fun loadCheckpoint(storedCheckpoint: StoredCheckpoint): Boolean {
        checkpointSupports.forEach { it.restoreCheckpoint(storedCheckpoint.data) }
        return true
    }
}

class CheckpointValidationException : Exception("Checkpoint validation failed")