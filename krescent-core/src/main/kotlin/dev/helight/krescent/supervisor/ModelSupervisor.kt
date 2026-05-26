package dev.helight.krescent.supervisor

import dev.helight.krescent.model.ReadModelBase
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import org.slf4j.LoggerFactory
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.milliseconds

class ModelSupervisor(
    val keepAliveChecks: Long = 100L,
) {

    private val notifications = Channel<Unit>(Channel.CONFLATED)
    private val logger = LoggerFactory.getLogger(ModelSupervisor::class.java)
    private var currentJob: CoroutineScope? = null
    private var panic: Exception? = null
    private var enqueuedRestart = false

    private val stateFlow: MutableStateFlow<SupervisorState> = MutableStateFlow(SupervisorState.STOPPED)
    val state: StateFlow<SupervisorState> get() = stateFlow
    val isHealthy: Boolean get() = state.value == SupervisorState.RUNNING

    val jobs = mutableListOf<ScheduledJob>()
    val startupMutex = Mutex()

    inline fun <reified T> modelOf(): T? {
        return jobs.map { it.given.current }.filterIsInstance<T>().firstOrNull()
    }

    fun <T : Any> modelOf(clazz: KClass<T>): T? {
        return jobs.map { it.given.current }.firstOrNull { clazz.isInstance(it) } as? T
    }

    fun <T : ReadModelBase> accessorOf(clazz: KClass<T>): ReadModelAccessor.Supervisor<T> {
        return ReadModelAccessor.Supervisor(this, clazz)
    }

    inline fun <reified T : ReadModelBase> accessorOf(): ReadModelAccessor.Supervisor<T> {
        return accessorOf(T::class)
    }


    fun register(job: ModelJob) {
        if (!state.value.startable) error("Cannot register new jobs while supervisor is not stopped.")
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
        if (!state.value.startable) error("Supervisor is already running.")
        supervisor()
    }

    suspend fun launch(context: CoroutineScope) = coroutineScope {
        if (!state.value.startable) error("Supervisor is already running.")
        stateFlow.value = SupervisorState.STOPPED
        val awaitable = async {
            state.first {
                check(it != SupervisorState.PANIC) { "Supervisor encountered a panic." }
                it == SupervisorState.RUNNING
            }
        }
        context.launch { supervisor() }
        awaitable.await()
    }

    suspend fun waitReady() {
        if (isHealthy) return
        if (state.value.startable) error("Supervisor is not running.")
        if (state.value == SupervisorState.PANIC) error("Supervisor encountered a panic.")
        state.first { it == SupervisorState.RUNNING }
    }

    suspend fun restartJobs() {
        if (state.value != SupervisorState.RUNNING) error("Supervisor is not running.")
        stateFlow.value = SupervisorState.STARTING
        enqueuedRestart = true
        notifications.send(Unit)
        logger.info("Waiting for model supervisor to restart all jobs...")
        waitReady()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun supervisor() = coroutineScope {
        currentJob = this
        panic = null
        val supervision = SupervisorJob()
        val context = supervision + Dispatchers.Default
        try {
            stateFlow.value = SupervisorState.STARTING
            logger.debug("Starting model supervisor.")
            while (isActive) {
                panic?.let {
                    stateFlow.value = SupervisorState.PANIC
                    throw it
                }

                var allReady = true
                if (enqueuedRestart) {
                    allReady = false
                    enqueuedRestart = false
                    for (scheduled in jobs) {
                        scheduled.kill()
                    }
                    stateFlow.value = SupervisorState.STARTING
                }

                for (scheduled in jobs) {
                    if (!scheduled.isActive || !scheduled.given.ready) allReady = false
                    if (scheduled.isActive) continue
                    if (!scheduled.given.condition(this@ModelSupervisor)) continue
                    scheduled.given.onBefore(this@ModelSupervisor)
                    recreateJob(scheduled, context)
                    panic?.let {
                        stateFlow.value = SupervisorState.PANIC
                        throw it
                    }
                }

                val currentState = stateFlow.value
                if (allReady && currentState != SupervisorState.RUNNING) {
                    stateFlow.value = SupervisorState.RUNNING
                    logger.debug("Model supervisor is now running.")
                } else if (!allReady && currentState == SupervisorState.RUNNING) {
                    stateFlow.value = SupervisorState.RECOVERING
                }

                select {
                    notifications.onReceive {}
                    onTimeout(keepAliveChecks.milliseconds) {}
                }
            }
        } finally {
            withContext(NonCancellable) {
                supervision.cancelAndJoin()
                for (scheduled in jobs) {
                    scheduled.error = null
                    scheduled.job = null
                }
                stateFlow.value = SupervisorState.STOPPED
                logger.debug("Model supervisor has exited.")
            }
        }
    }

    private suspend fun recreateJob(scheduled: ScheduledJob, context: CoroutineContext) {
        scheduled.kill()
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

    class ScheduledJob(
        val given: ModelJob,
    ) {
        var job: Job? = null
        var error: Throwable? = null

        val isActive: Boolean
            get() = job?.isActive == true && error == null

        override fun toString(): String {
            return "ScheduledJob(given=$given, job=$job, error=$error, isActive=$isActive)"
        }

        suspend fun kill() {
            val hasJob = job?.isActive == true
            if (!hasJob) return
            job?.cancel()
            given.onKilled()
            job = null
        }

    }

    enum class SupervisorState(val startable: Boolean = false) {
        STOPPED(true),
        PANIC(true),
        STARTING,
        RUNNING,
        RECOVERING,
    }
}

