package dev.helight.krescent.supervisor

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlin.test.Test
import kotlin.test.assertEquals

class ModelSupervisorTest {

    @Test
    fun `Test restarting after a crash`() = runBlocking {
        withTimeout(500) {
            val supervisor = ModelSupervisor()
            val job = CrashingModelJob(timesToCrash = 1)
            supervisor.register(job)
            val supervisorJob = async { supervisor.execute() }
            delay(100)
            supervisorJob.cancelAndJoin()
            assertEquals(0, job.timesToCrash)
            assertEquals(1, job.timesCompleted)
            assertEquals(1, job.timesFailCaught)
        }
    }

    @Test
    fun `Test preconditions using a locking job`() = runBlocking {
        withTimeout(1000) {
            val supervisor = ModelSupervisor(1)
            val mutex = Mutex()
            val jobs = List(5) { MutexClaimingJob(mutex) }
            supervisor.register(jobs)
            val supervisorJob = async { supervisor.execute() }
            delay(25)
            assertEquals(1, jobs.count { it.hasLocked })
            assertEquals(0, jobs.count { it.hasCompleted })
            delay(50)
            assertEquals(1, jobs.count { it.hasCompleted })
            delay(300)
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
        delay(1000)
        println()
    }

    override suspend fun onFailed(supervisor: ModelSupervisor, error: Throwable) {
        timesFailCaught++
    }
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
        delay(50)
        mutex.unlock()
        hasCompleted = true
        delay(1000)
    }
}