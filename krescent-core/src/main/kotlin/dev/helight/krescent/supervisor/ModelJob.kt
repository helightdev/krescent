package dev.helight.krescent.supervisor

interface ModelJob {
    suspend fun condition(supervisor: ModelSupervisor) = !supervisor.startupMutex.isLocked
    suspend fun run(supervisor: ModelSupervisor)
    suspend fun onBefore(supervisor: ModelSupervisor) {}
    suspend fun onFailed(supervisor: ModelSupervisor, error: Throwable) {}
    suspend fun onExited(supervisor: ModelSupervisor) {}
}

