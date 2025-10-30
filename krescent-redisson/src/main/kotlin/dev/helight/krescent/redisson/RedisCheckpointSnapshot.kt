package dev.helight.krescent.redisson

import dev.helight.krescent.checkpoint.CheckpointBucket
import dev.helight.krescent.checkpoint.StoredCheckpoint
import kotlinx.datetime.Instant
import java.io.Serializable

internal open class RedisCheckpointSnapshot(
    open val version: String,
    open val position: String,
    open val timestamp: Long,
    open val data: ByteArray,
) : Serializable {
    fun toStoredCheckpoint(namespace: String): StoredCheckpoint {
        return StoredCheckpoint(
            version = version,
            position = position,
            timestamp = Instant.fromEpochMilliseconds(timestamp),
            namespace = namespace,
            data = CheckpointBucket.fromByteArray(data)
        )
    }

    companion object {
        fun fromStoredCheckpoint(checkpoint: StoredCheckpoint): RedisCheckpointSnapshot {
            return RedisCheckpointSnapshot(
                version = checkpoint.version,
                position = checkpoint.position,
                timestamp = checkpoint.timestamp.toEpochMilliseconds(),
                data = checkpoint.data.encodeToByteArray()
            )
        }
    }
}