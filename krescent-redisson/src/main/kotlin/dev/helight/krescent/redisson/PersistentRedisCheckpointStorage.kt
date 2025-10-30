package dev.helight.krescent.redisson

import dev.helight.krescent.checkpoint.CheckpointStorage
import dev.helight.krescent.checkpoint.StoredCheckpoint
import kotlinx.coroutines.future.asDeferred
import org.redisson.api.RedissonClient

class PersistentRedisCheckpointStorage(
    val client: RedissonClient,
    val mapName: String = "krescent:checkpoints",
) : CheckpointStorage {
    override suspend fun storeCheckpoint(checkpoint: StoredCheckpoint) {
        client.getMap<String, RedisCheckpointSnapshot>(mapName)
            .putAsync(checkpoint.namespace, RedisCheckpointSnapshot.fromStoredCheckpoint(checkpoint))
            .asDeferred().await()
    }

    override suspend fun getLatestCheckpoint(namespace: String): StoredCheckpoint? {
        val snapshot = client.getMap<String, RedisCheckpointSnapshot>(mapName)
            .getAsync(namespace)
            .asDeferred().await()
        return snapshot?.toStoredCheckpoint(namespace)
    }

    override suspend fun deleteCheckpoint(namespace: String) {
        client.getMap<String, RedisCheckpointSnapshot>(mapName)
            .removeAsync(namespace)
            .asDeferred().await()
    }

    override suspend fun clearCheckpoints() {
        client.getMap<String, RedisCheckpointSnapshot>(mapName)
            .clearAsync()
            .asDeferred().await()
    }
}

