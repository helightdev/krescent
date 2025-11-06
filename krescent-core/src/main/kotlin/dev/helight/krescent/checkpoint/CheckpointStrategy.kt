package dev.helight.krescent.checkpoint

import dev.helight.krescent.event.EventMessage
import dev.helight.krescent.source.StreamingToken
import kotlinx.datetime.Clock
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.time.Duration

interface CheckpointStrategy {
    suspend fun afterMessage(eventMessage: EventMessage, context: CheckpointContext): Boolean = false
    suspend fun afterTermination(context: CheckpointContext): Boolean = false
    suspend fun afterCallback(context: CheckpointContext): Boolean = false
}

data class CheckpointContext(
    val lastCheckpoint: StoredCheckpoint?,
    val lastPosition: StreamingToken<*>?,
)

@Suppress("unused")
object NoCheckpointStrategy : CheckpointStrategy

@Suppress("unused")
object TerminationCheckpointStrategy : CheckpointStrategy {
    override suspend fun afterMessage(eventMessage: EventMessage, context: CheckpointContext): Boolean {
        return false
    }

    override suspend fun afterTermination(context: CheckpointContext): Boolean {
        return true
    }
}

@Suppress("unused")
class MinimizedCheckpointStrategy : CheckpointStrategy {

    private var lastPosition: StreamingToken<*>? = null

    override suspend fun afterTermination(context: CheckpointContext): Boolean {
        return checkpointIfChanged(context)
    }

    override suspend fun afterCallback(context: CheckpointContext): Boolean {
        return checkpointIfChanged(context)
    }

    private fun checkpointIfChanged(context: CheckpointContext): Boolean {
        if (context.lastPosition == null) return false
        if (lastPosition == null) {
            lastPosition = context.lastPosition
            return true
        }
        return lastPosition!!.compareUnsafe(context.lastPosition) != 0
    }
}


@Suppress("unused")
@OptIn(ExperimentalAtomicApi::class)
class FixedEventRateCheckpointStrategy(
    private val checkpoint: Long,
    val checkpointOnTermination: Boolean = true,
) : CheckpointStrategy {

    private val counter = AtomicLong(0)

    override suspend fun afterTermination(context: CheckpointContext): Boolean {
        return checkpointOnTermination
    }

    override suspend fun afterMessage(eventMessage: EventMessage, context: CheckpointContext): Boolean {
        return counter.incrementAndFetch() % checkpoint == 0L
    }
}

@Suppress("unused")
class ManualCheckpointStrategy : CheckpointStrategy {

    private var shouldCheckpoint = false

    fun mark() {
        shouldCheckpoint = true
    }

    override suspend fun afterMessage(eventMessage: EventMessage, context: CheckpointContext): Boolean {
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
    val checkpointOnTermination: Boolean = true,
) : CheckpointStrategy {

    override suspend fun afterTermination(context: CheckpointContext): Boolean {
        return checkpointOnTermination
    }

    override suspend fun afterMessage(eventMessage: EventMessage, context: CheckpointContext): Boolean {
        if (context.lastCheckpoint == null) return true
        val currentTime = Clock.System.now()
        val duration = currentTime - (context.lastCheckpoint.timestamp)
        return duration >= rate
    }
}

@Suppress("unused")
object AlwaysCheckpointStrategy : CheckpointStrategy {

    override suspend fun afterMessage(eventMessage: EventMessage, context: CheckpointContext): Boolean {
        return true
    }
}