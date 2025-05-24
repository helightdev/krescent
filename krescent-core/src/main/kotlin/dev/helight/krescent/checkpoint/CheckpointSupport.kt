package dev.helight.krescent.checkpoint

import dev.helight.krescent.HandlerChainParticipant

interface CheckpointSupport {
    suspend fun createCheckpoint(bucket: CheckpointBucket)
    suspend fun restoreCheckpoint(bucket: CheckpointBucket)

    companion object {
        suspend fun storagePass(participant: HandlerChainParticipant, bucket: CheckpointBucket) {
            if (participant is CheckpointSupport) {
                participant.createCheckpoint(bucket)
            }
        }

        suspend fun restorePass(participant: HandlerChainParticipant, bucket: CheckpointBucket) {
            if (participant is CheckpointSupport) {
                participant.restoreCheckpoint(bucket)
            }
        }
    }
}