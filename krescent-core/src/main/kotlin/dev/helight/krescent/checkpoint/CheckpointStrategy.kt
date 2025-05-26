package dev.helight.krescent.checkpoint

import dev.helight.krescent.event.EventMessage
import java.time.Instant
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.time.Duration

interface CheckpointStrategy {
    suspend fun tick(eventMessage: EventMessage, lastCheckpoint: StoredCheckpoint?): Boolean
    suspend fun tickGracefulTermination(): Boolean = false
}

@Suppress("unused")
object NoCheckpointStrategy : CheckpointStrategy {
    override suspend fun tick(eventMessage: EventMessage, lastCheckpoint: StoredCheckpoint?): Boolean {
        return false
    }
}

@Suppress("unused")
@OptIn(ExperimentalAtomicApi::class)
class FixedEventRateCheckpointStrategy(
    private val checkpoint: Long,
) : CheckpointStrategy {

    private val counter = AtomicLong(0)

    override suspend fun tick(eventMessage: EventMessage, lastCheckpoint: StoredCheckpoint?): Boolean {
        return counter.incrementAndFetch() % checkpoint == 0L
    }
}

@Suppress("unused")
class ManualCheckpointStrategy() : CheckpointStrategy {

    private var shouldCheckpoint = false

    fun mark() {
        shouldCheckpoint = true
    }

    override suspend fun tick(eventMessage: EventMessage, lastCheckpoint: StoredCheckpoint?): Boolean {
        if (shouldCheckpoint) {
            shouldCheckpoint = false
            return true
        }
        return false
    }

}

@Suppress("unused")
class FixedTimeRateCheckpointStrategy(
    private val rate: Duration,
) : CheckpointStrategy {

    override suspend fun tick(eventMessage: EventMessage, lastCheckpoint: StoredCheckpoint?): Boolean {
        if (lastCheckpoint == null) return true
        val currentTime = Instant.now()
        val duration = currentTime.minusMillis(lastCheckpoint.timestamp.toEpochMilli()).toEpochMilli()
        return duration >= rate.inWholeMilliseconds
    }
}

@Suppress("unused")
object AlwaysCheckpointStrategy : CheckpointStrategy {
    override suspend fun tick(eventMessage: EventMessage, lastCheckpoint: StoredCheckpoint?): Boolean {
        return true
    }
}