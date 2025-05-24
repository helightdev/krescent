package dev.helight.krescent.checkpoints

import dev.helight.krescent.EventMessage
import java.time.Instant
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.fetchAndIncrement
import kotlin.time.Duration

interface CheckpointStrategy {
    suspend fun tick(eventMessage: EventMessage, lastCheckpoint: StoredCheckpoint?): Boolean
}

class NoCheckpointStrategy : CheckpointStrategy {
    override suspend fun tick(eventMessage: EventMessage, lastCheckpoint: StoredCheckpoint?): Boolean {
        return false
    }
}

@OptIn(ExperimentalAtomicApi::class)
class FixedEventRateCheckpointStrategy(
    private val checkpoint: Long,
) : CheckpointStrategy {

    private val counter = AtomicLong(0)

    override suspend fun tick(eventMessage: EventMessage, lastCheckpoint: StoredCheckpoint?): Boolean {
        return counter.fetchAndIncrement() % checkpoint == 0L
    }
}

class FixedTimeRateCheckpointStrategy(
    private val rate: Duration
) : CheckpointStrategy {

    override suspend fun tick(eventMessage: EventMessage, lastCheckpoint: StoredCheckpoint?): Boolean {
        if (lastCheckpoint == null) return true
        val currentTime = Instant.now()
        val duration = currentTime.minusMillis(lastCheckpoint.timestamp.toEpochMilli()).toEpochMilli()
        return duration >= rate.inWholeMilliseconds
    }
}