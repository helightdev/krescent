package dev.helight.krescent.checkpoints

import dev.helight.krescent.HandlerChainParticipant
import dev.helight.krescent.serialization.InstantSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.time.Instant

class CheckpointBucket(
    val buffer: MutableMap<String, JsonElement>,
) {

    operator fun set(key: String, value: JsonElement) {
        buffer.put(key, value)
    }

    operator fun get(key: String): JsonElement? {
        return buffer[key]
    }


    fun buildJsonObject(): JsonElement = JsonObject(buffer)

    override fun toString(): String = "CheckpointBucket{buffer=$buffer}"

}

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


interface CheckpointStorage {
    suspend fun storeCheckpoint(checkpoint: StoredCheckpoint)
    suspend fun getLatestCheckpoint(namespace: String): StoredCheckpoint?
    suspend fun clearCheckpoints()
}

@Serializable
data class StoredCheckpoint(
    val namespace: String,
    val revision: Long,
    val position: String,
    @Serializable(with = InstantSerializer::class)
    val timestamp: Instant,
    val data: JsonElement,
)

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