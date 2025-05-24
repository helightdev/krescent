package dev.helight.krescent.checkpoint

interface CheckpointStorage {
    suspend fun storeCheckpoint(checkpoint: StoredCheckpoint)
    suspend fun getLatestCheckpoint(namespace: String): StoredCheckpoint?
    suspend fun clearCheckpoints()
}