package dev.helight.krescent.checkpoint

import dev.helight.krescent.event.*
import dev.helight.krescent.source.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory

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

    private val logger = LoggerFactory.getLogger(CheckpointingEventSourceConsumer::class.java)
    private var lastCheckpoint: StoredCheckpoint? = null
    private var lastPosition: StreamingToken<*>? = null
    private val context: CheckpointContext
        get() = CheckpointContext(
            lastCheckpoint = lastCheckpoint,
            lastPosition = lastPosition
        )

    @Suppress("UNCHECKED_CAST")
    override suspend fun strategy(strategy: EventSourcingStrategy) {
        loadLastCheckpoint()
        if (strategy is CallbackEventSourcingStrategy) {
            strategy.addThenChain {
                if (checkpointStrategy.afterCallback(context)) tryCheckpoint()
            }
        }

        try {
            strategy.source(source, lastPosition, object : EventMessageStreamProcessor {
                override suspend fun process(
                    message: EventMessage,
                    position: StreamingToken<*>,
                ) {
                    consumer.process(message, position)
                    lastPosition = position
                    if (checkpointStrategy.afterMessage(message, context)) tryCheckpoint()
                }

                override suspend fun forwardSystemEvent(event: Event) {
                    consumer.forwardSystemEvent(event)
                }
            })
        } catch (e: CancellationException) {
            withContext(NonCancellable) { if (checkpointStrategy.afterTermination(context)) tryCheckpoint() }
            throw e
        }

        if (checkpointStrategy.afterTermination(context)) tryCheckpoint()
    }

    private suspend fun tryCheckpoint() {
        if (lastPosition == null) return
        val checkpoint = checkpoint(lastPosition!!)
        checkpointStorage.storeCheckpoint(checkpoint)
        lastCheckpoint = checkpoint
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
        this.lastCheckpoint = lastCheckpoint
        this.lastPosition = lastCheckpoint?.position?.let { source.deserializeToken(it) }
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