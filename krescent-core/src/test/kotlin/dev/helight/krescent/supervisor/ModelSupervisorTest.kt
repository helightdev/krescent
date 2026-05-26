package dev.helight.krescent.supervisor

import dev.helight.krescent.bookstore.BooksAvailableReadModel
import dev.helight.krescent.bookstore.bookstoreSimulatedEventStream
import dev.helight.krescent.event.logging.ConsoleLoggingEventStreamProcessor.Companion.useConsoleLogging
import dev.helight.krescent.model.EventModelBase.Extension.withConfiguration
import dev.helight.krescent.model.ReadModelBase
import dev.helight.krescent.source.impl.InMemoryEventStore
import dev.helight.krescent.source.impl.SimulatedDelayStreamingEventSource
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

class ModelSupervisorTest {

    @Test
    fun `Test the new awaiting mechanism`() = runBlocking {
        val supervisor = ModelSupervisor()
        val job = CrashingModelJob(timesToCrash = 1)
        supervisor.register(job)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        supervisor.launch(scope)
        assertEquals(0, job.timesToCrash)
        assertEquals(1, job.timesCompleted)
        assertEquals(1, job.timesFailCaught)
        scope.cancel()
    }

    @Test
    fun `Test with bookstore ReadModel`() = runBlocking {
        val supervisor = ModelSupervisor()
        val crashCount = AtomicInteger(1)
        supervisor.register(
            BalancedReadModelJob(
                modelSupplier = {
                    BooksAvailableReadModel(crashCount = crashCount).withConfiguration {
                        useConsoleLogging()
                    }
                },
                sourceSupplier = {
                    SimulatedDelayStreamingEventSource(
                        100, 1,
                        InMemoryEventStore(bookstoreSimulatedEventStream.toMutableList()),
                    )

                }
            )
        )
        val accessor = supervisor.accessorOf<BooksAvailableReadModel>()
        assert(!accessor.isPresent())

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        supervisor.launch(scope)
        val model = supervisor.modelOf<BooksAvailableReadModel>()!!
        assertEquals(9, model.target["1"])

        assert(accessor.isPresent())
        assertEquals(9, accessor.get().target["1"])

        supervisor.restartJobs()
        assertEquals(9, accessor.get().target["1"])
    }

    @Test
    fun `Test restarting after a crash`() = runBlocking {
        withTimeout(500.milliseconds) {
            val supervisor = ModelSupervisor()
            val job = CrashingModelJob(timesToCrash = 1)
            supervisor.register(job)
            val supervisorJob = async { supervisor.execute() }
            delay(100.milliseconds)
            supervisorJob.cancelAndJoin()
            assertEquals(0, job.timesToCrash)
            assertEquals(1, job.timesCompleted)
            assertEquals(1, job.timesFailCaught)
        }
    }

    @Test
    fun `Test preconditions using a locking job`() = runBlocking {
        withTimeout(1000.milliseconds) {
            val supervisor = ModelSupervisor(1)
            val mutex = Mutex()
            val jobs = List(5) { MutexClaimingJob(mutex) }
            supervisor.register(jobs)
            val supervisorJob = async { supervisor.execute() }
            delay(25.milliseconds)
            assertEquals(1, jobs.count { it.hasLocked })
            assertEquals(0, jobs.count { it.hasCompleted })
            delay(50.milliseconds)
            assertEquals(1, jobs.count { it.hasCompleted })
            delay(300.milliseconds)
            supervisorJob.cancelAndJoin()
            assertEquals(5, jobs.count { it.hasCompleted })
            assertEquals(5, jobs.count { it.hasLocked })
        }
    }

}

class CrashingModelJob(
    var timesToCrash: Int,
) : ModelJob {

    var timesCompleted: Int = 0
    var timesFailCaught: Int = 0

    override suspend fun run(supervisor: ModelSupervisor) {
        if (timesToCrash > 0) {
            timesToCrash--
            throw RuntimeException("Crash!")
        }
        timesCompleted++
        delay(1000.milliseconds)
        println("Exit")
    }

    override suspend fun onFailed(supervisor: ModelSupervisor, error: Throwable) {
        timesFailCaught++
    }

    override val current: ReadModelBase?
        get() = null
}

class MutexClaimingJob(
    val mutex: Mutex,
) : ModelJob {
    var hasLocked = false
    var hasCompleted = false

    override suspend fun condition(supervisor: ModelSupervisor): Boolean {
        return mutex.tryLock()
    }

    override suspend fun run(supervisor: ModelSupervisor) {
        hasLocked = true
        delay(50.milliseconds)
        mutex.unlock()
        hasCompleted = true
        delay(1000.milliseconds)
    }

    override val current: ReadModelBase?
        get() = null
}