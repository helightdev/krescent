package dev.helight.krescent.redisson

import dev.helight.krescent.checkpoint.CheckpointStorage
import dev.helight.krescent.checkpoint.StoredCheckpoint
import kotlinx.coroutines.future.asDeferred
import org.redisson.api.RedissonClient
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class CachedRedisCheckpointStorage(
    val client: RedissonClient,
    val cacheName: String = "krescent:checkpoints:cache",
    val ttl: Duration = 30.toDuration(DurationUnit.DAYS),
) : CheckpointStorage {

    override suspend fun storeCheckpoint(checkpoint: StoredCheckpoint) {
        val millis = ttl.inWholeMilliseconds
        client.getMapCache<String, RedisCheckpointSnapshot>(cacheName)
            .putAsync(
                checkpoint.namespace,
                RedisCheckpointSnapshot.fromStoredCheckpoint(checkpoint),
                millis,
                TimeUnit.MILLISECONDS
            )
            .asDeferred().await()
    }

    override suspend fun getLatestCheckpoint(namespace: String): StoredCheckpoint? {
        val snapshot = client.getMapCache<String, RedisCheckpointSnapshot>(cacheName)
            .getAsync(namespace)
            .asDeferred().await()
        return snapshot?.toStoredCheckpoint(namespace)
    }

    override suspend fun deleteCheckpoint(namespace: String) {
        client.getMapCache<String, RedisCheckpointSnapshot>(cacheName)
            .removeAsync(namespace)
            .asDeferred().await()
    }

    override suspend fun clearCheckpoints() {
        client.getMapCache<String, RedisCheckpointSnapshot>(cacheName)
            .clearAsync()
            .asDeferred().await()
    }
}

