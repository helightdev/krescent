package dev.helight.krescent.supervisor

import dev.helight.krescent.model.ReadModelBase
import kotlinx.coroutines.withTimeout
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

interface ReadModelAccessor<T : ReadModelBase> {

    fun isPresent(): Boolean
    suspend fun get(): T
    suspend fun getTimeout(timeout: Duration = 10.seconds): T = withTimeout(timeout) { get() }

    class Supervisor<T : ReadModelBase>(
        val supervisor: ModelSupervisor,
        val clazz: KClass<T>,
    ) : ReadModelAccessor<T> {
        override fun isPresent(): Boolean = supervisor.isHealthy && supervisor.modelOf(clazz) != null

        override suspend fun get(): T {
            supervisor.waitReady()
            return supervisor.modelOf(clazz)
                ?: error("Model of type ${clazz.simpleName} is not available in the supervisor.")
        }
    }

    class Fixed<T : ReadModelBase>(
        val readModel: T,
    ) : ReadModelAccessor<T> {
        override fun isPresent(): Boolean = true
        override suspend fun get(): T = readModel
    }
}