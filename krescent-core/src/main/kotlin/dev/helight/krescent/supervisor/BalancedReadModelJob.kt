package dev.helight.krescent.supervisor

import dev.helight.krescent.model.ReadModelBase
import dev.helight.krescent.model.ReadModelBase.Extension.strategy
import dev.helight.krescent.model.ReadModelBase.Extension.stream
import dev.helight.krescent.source.StreamingEventSource
import dev.helight.krescent.source.strategy.StreamingSourcingStrategy
import org.slf4j.LoggerFactory
import kotlin.math.min
import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class BalancedReadModelJob(
    val modelSupplier: () -> ReadModelBase,
    val source: StreamingEventSource? = null,
    val retryAttempts: Int = 3,
    val retryDelay: Duration = 1.toDuration(DurationUnit.SECONDS),
    val retryJitter: Duration = 200.toDuration(DurationUnit.MILLISECONDS),
    val retryResetDelay: Duration = 10.toDuration(DurationUnit.SECONDS),
    val maxRetryDelay: Duration = 60.toDuration(DurationUnit.SECONDS),
    val retryIntervalFunction: (Int) -> Duration = { attempt ->
        exponentialBackoff(
            attempt,
            retryDelay,
            retryJitter
        )
    },
    val sourceSupplier: () -> StreamingEventSource = {
        source ?: error("BalancedReadModelJob requires a source or sourceSupplier to be provided")
    },
    val preventParallelCatchup: Boolean = true,
) : ModelJob {

    private var currentAttempt = 0
    private var timeout: Long = 0L
    private var lastStart: Long = 0L

    override suspend fun condition(supervisor: ModelSupervisor): Boolean {
        return !supervisor.startupMutex.isLocked && System.currentTimeMillis() > timeout
    }

    override suspend fun onBefore(supervisor: ModelSupervisor) {
        if (preventParallelCatchup) supervisor.startupMutex.lock(this)
        lastStart = System.currentTimeMillis()
    }

    override suspend fun run(supervisor: ModelSupervisor) {
        val source = sourceSupplier()
        val model = modelSupplier()
        if (preventParallelCatchup) {
            model.strategy(source, StreamingSourcingStrategy {
                supervisor.startupMutex.unlock(this)
            })
        } else {
            model.stream(source)
        }
    }

    override suspend fun onExited(supervisor: ModelSupervisor) {
        if (supervisor.startupMutex.holdsLock(this)) {
            supervisor.startupMutex.unlock(this)
        }
    }

    @Suppress("ConvertTwoComparisonsToRangeCheck")
    override suspend fun onFailed(supervisor: ModelSupervisor, error: Throwable) {
        if (supervisor.startupMutex.holdsLock(this)) {
            supervisor.startupMutex.unlock(this)
        }

        if (System.currentTimeMillis() - lastStart >= retryResetDelay.toLong(DurationUnit.MILLISECONDS)) {
            currentAttempt = 0
        }

        currentAttempt++
        if (currentAttempt > retryAttempts && retryAttempts >= 0) {
            supervisor.panic("BalancedReadModelJob has exceeded maximum retry attempts ($retryAttempts).", error)
        } else {
            val delay =
                min(retryIntervalFunction(currentAttempt).inWholeMilliseconds, maxRetryDelay.inWholeMilliseconds)
            logger.info("BalancedReadModelJob will retry in $delay (attempt $currentAttempt of $retryAttempts).")
            timeout = System.currentTimeMillis() + delay
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(BalancedReadModelJob::class.java)

        internal fun exponentialBackoff(attempt: Int, baseDelay: Duration, jitter: Duration): Duration {
            val exponentialDelay = baseDelay.toDouble(DurationUnit.MILLISECONDS) * 2.0.pow(attempt - 1)
            val jitterDelay = Math.random() * jitter.toDouble(DurationUnit.MILLISECONDS)
            return (exponentialDelay + jitterDelay).toDuration(DurationUnit.MILLISECONDS)
        }
    }
}