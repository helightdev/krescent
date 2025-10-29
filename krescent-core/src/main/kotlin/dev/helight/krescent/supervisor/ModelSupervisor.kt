package dev.helight.krescent.supervisor

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import org.slf4j.LoggerFactory
import kotlin.coroutines.CoroutineContext

class ModelSupervisor(
    val keepAliveChecks: Long = 100L,
) {

    private val jobs = mutableListOf<ScheduledJob>()
    private val notifications = Channel<Unit>(Channel.CONFLATED)
    private val logger = LoggerFactory.getLogger(ModelSupervisor::class.java)
    private var started = false
    private var currentJob: CoroutineScope? = null
    private var panic: Exception? = null
    val startupMutex = Mutex()

    fun register(job: ModelJob) {
        if (started) error("Cannot register new jobs after the supervisor has started.")
        jobs.add(ScheduledJob(job))
    }

    fun register(vararg jobs: ModelJob) {
        for (job in jobs) register(job)
    }

    fun register(jobs: Collection<ModelJob>) {
        for (job in jobs) register(job)
    }

    fun panic(message: String, cause: Throwable? = null) {
        panic = SupervisorPanicException(message, cause)
        notifications.trySend(Unit)
    }

    suspend fun execute() {
        if (started) error("Supervisor is already running.")
        supervisor()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun supervisor() = coroutineScope {
        currentJob = this
        panic = null
        val supervision = SupervisorJob()
        val context = supervision + Dispatchers.Default
        try {
            started = true
            logger.debug("Starting model supervisor.")
            while (isActive) {
                panic?.let { throw it }
                for (scheduled in jobs) {
                    if (scheduled.isActive) continue
                    if (!scheduled.given.condition(this@ModelSupervisor)) continue
                    scheduled.given.onBefore(this@ModelSupervisor)
                    recreateJob(scheduled, context)
                    panic?.let { throw it }
                }
                select {
                    notifications.onReceive {}
                    onTimeout(keepAliveChecks) {}
                }
            }
        } finally {
            withContext(NonCancellable) {
                supervision.cancelAndJoin()
                for (scheduled in jobs) {
                    scheduled.error = null
                    scheduled.job = null
                }
                started = false
                logger.debug("Model supervisor has exited.")
            }
        }
    }

    private fun recreateJob(scheduled: ScheduledJob, context: CoroutineContext) {
        scheduled.error = null
        val handler = CoroutineExceptionHandler { _, throwable ->
            if (throwable is CancellationException) return@CoroutineExceptionHandler // Ignore cancellations
            runBlocking {
                scheduled.error = throwable
                logger.error("Model job '{}' has failed with an error.", scheduled.given, throwable)
                scheduled.given.onFailed(this@ModelSupervisor, throwable)
                notifications.send(Unit)
            }
        }
        val job = CoroutineScope(context).launch(handler) {
            scheduled.given.run(this@ModelSupervisor)
            logger.debug("Model job '{}' has exited gracefully.", scheduled.given)
            scheduled.given.onExited(this@ModelSupervisor)
            notifications.trySend(Unit)
        }
        scheduled.job = job
        logger.debug("Model job '{}' has been (re)started.", scheduled.given)
    }

    private class ScheduledJob(
        val given: ModelJob,
    ) {
        var job: Job? = null
        var error: Throwable? = null

        val isActive: Boolean
            get() = job?.isActive == true && error == null
    }
}

