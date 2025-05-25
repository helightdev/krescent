package dev.helight.krescent.checkpoint.impl

import dev.helight.krescent.checkpoint.CheckpointStorage
import dev.helight.krescent.checkpoint.StoredCheckpoint
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * An in-memory implementation of the `CheckpointStorage` interface used to store and retrieve
 * checkpoint data. This implementation holds checkpoints in a mutable map during runtime
 * and provides methods for serialization and loading of the persisted checkpoint state.
 */
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

    /**
     * Serializes the current state of the in-memory checkpoints into a ByteArray format.
     *
     * @return a ByteArray representation of the serialized checkpoint state.
     */
    @Suppress("unused")
    fun serialize(): ByteArray {
        return Json.Default.encodeToString(SerializedState(checkpoints)).encodeToByteArray()
    }

    /**
     * Deserializes a given serialized checkpoint state into the in-memory storage,
     * replacing the current state with the deserialized state.
     *
     * @param data the serialized checkpoint state as a ByteArray.
     */
    fun load(data: ByteArray) {
        val state = Json.Default.decodeFromString<SerializedState>(data.decodeToString())
        checkpoints.clear()
        checkpoints.putAll(state.checkpoints)
    }

    @Serializable
    data class SerializedState(
        val checkpoints: Map<String, StoredCheckpoint>,
    )
}