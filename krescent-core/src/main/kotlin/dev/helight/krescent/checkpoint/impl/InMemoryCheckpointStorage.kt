package dev.helight.krescent.checkpoint.impl

import dev.helight.krescent.checkpoint.CheckpointStorage
import dev.helight.krescent.checkpoint.StoredCheckpoint
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray

/**
 * An in-memory implementation of the `CheckpointStorage` interface used to store and retrieve
 * checkpoint data. This implementation holds checkpoints in a mutable map during runtime
 * and provides methods for serialization and loading of the persisted checkpoint state.
 */
class InMemoryCheckpointStorage : CheckpointStorage {
    private val checkpoints = mutableMapOf<String, StoredCheckpoint>()
    private val mutex = Mutex()

    override suspend fun storeCheckpoint(checkpoint: StoredCheckpoint) = mutex.withLock {
        checkpoints[checkpoint.namespace] = checkpoint
    }

    override suspend fun getLatestCheckpoint(namespace: String): StoredCheckpoint? = mutex.withLock {
        return checkpoints[namespace]
    }

    override suspend fun clearCheckpoints() = mutex.withLock {
        checkpoints.clear()
    }

    /**
     * Serializes the current state of the in-memory checkpoints into a ByteArray format.
     *
     * @return a ByteArray representation of the serialized checkpoint state.
     */
    @Suppress("unused")
    @OptIn(ExperimentalSerializationApi::class)
    fun serialize(): ByteArray {
        return Cbor.encodeToByteArray(SerializedState(checkpoints))
    }

    /**
     * Deserializes a given serialized checkpoint state into the in-memory storage,
     * replacing the current state with the deserialized state.
     *
     * @param data the serialized checkpoint state as a ByteArray.
     */
    @OptIn(ExperimentalSerializationApi::class)
    fun load(data: ByteArray) {
        val state = Cbor.decodeFromByteArray<SerializedState>(data)
        checkpoints.clear()
        checkpoints.putAll(state.checkpoints)
    }

    @Serializable
    data class SerializedState(
        val checkpoints: Map<String, StoredCheckpoint>,
    )
}