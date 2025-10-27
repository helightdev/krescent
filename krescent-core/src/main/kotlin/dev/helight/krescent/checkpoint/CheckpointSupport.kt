package dev.helight.krescent.checkpoint

interface CheckpointSupport {
    suspend fun createCheckpoint(bucket: CheckpointBucket)
    suspend fun restoreCheckpoint(bucket: CheckpointBucket)
    suspend fun validateCheckpoint(bucket: CheckpointBucket): Boolean = true
}