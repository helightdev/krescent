package dev.helight.krescent.checkpoint.impl

import dev.helight.krescent.checkpoint.CheckpointStorage
import dev.helight.krescent.checkpoint.StoredCheckpoint

class InMemoryCheckpointStorage : CheckpointStorage {
    private val checkpoints = mutableMapOf<String, StoredCheckpoint>()

    override suspend fun storeCheckpoint(checkpoint: StoredCheckpoint) {
        checkpoints[checkpoint.namespace] = checkpoint
    }

    override suspend fun getLatestCheckpoint(namespace: String): StoredCheckpoint? {
        return checkpoints[namespace]
    }

    override suspend fun clearCheckpoints() {
        checkpoints.clear()
    }
}