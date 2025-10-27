@file:Suppress("unused")

package dev.helight.krescent.checkpoint.impl

import dev.helight.krescent.checkpoint.CheckpointStorage
import dev.helight.krescent.checkpoint.StoredCheckpoint

/**
 * A [CheckpointStorage] wrapper that adds a prefix to the namespace of each checkpoint.
 */
class KeyedCheckpointStorage(
    val delegate: CheckpointStorage,
    val prefix: String,
) : CheckpointStorage {
    override suspend fun storeCheckpoint(checkpoint: StoredCheckpoint) {
        delegate.storeCheckpoint(checkpoint.copy(namespace = boxNamespace(checkpoint.namespace)))
    }

    override suspend fun getLatestCheckpoint(namespace: String): StoredCheckpoint? {
        return delegate.getLatestCheckpoint(boxNamespace(namespace))?.let {
            it.copy(namespace = unboxNamespace(it.namespace))
        }
    }

    override suspend fun deleteCheckpoint(namespace: String) {
        delegate.deleteCheckpoint(boxNamespace(namespace))
    }

    override suspend fun clearCheckpoints() {
        error("Clearing checkpoints is not supported in KeyedCheckpointStorage. Use the delegate directly if needed.")
    }

    private fun boxNamespace(namespace: String): String = "$prefix::$namespace"
    private fun unboxNamespace(namespace: String): String = namespace.removePrefix("$prefix::")

    companion object {
        fun CheckpointStorage.keyed(prefix: String): KeyedCheckpointStorage = KeyedCheckpointStorage(this, prefix)
    }
}