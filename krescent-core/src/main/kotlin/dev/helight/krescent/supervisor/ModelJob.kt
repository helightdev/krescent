package dev.helight.krescent.supervisor

import dev.helight.krescent.model.ReadModelBase

interface ModelJob {
    val current: ReadModelBase?
    val ready: Boolean
        get() = true

    suspend fun condition(supervisor: ModelSupervisor) = !supervisor.startupMutex.isLocked
    suspend fun run(supervisor: ModelSupervisor)
    suspend fun onBefore(supervisor: ModelSupervisor) {}
    suspend fun onFailed(supervisor: ModelSupervisor, error: Throwable) {}
    suspend fun onExited(supervisor: ModelSupervisor) {}
}

