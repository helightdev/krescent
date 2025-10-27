package dev.helight.krescent.test

import dev.helight.krescent.checkpoint.CheckpointBucket
import dev.helight.krescent.checkpoint.CheckpointStorage
import dev.helight.krescent.checkpoint.StoredCheckpoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

interface CheckpointStoreContract {

    fun withCheckpointStorage(block: suspend CoroutineScope.(CheckpointStorage) -> Unit)

    @Test
    fun `Store and retrieve a checkpoint`() = withCheckpointStorage { store ->
        val input = StoredCheckpoint("projection", "1", "1", Clock.System.now(), CheckpointBucket())
        store.storeCheckpoint(input)
        val retrieved = store.getLatestCheckpoint("projection")
        assertNotNull(retrieved)
        val nonExistent = store.getLatestCheckpoint("other")
        assertNull(nonExistent)
    }

    @Test
    fun `Ensure overrides are applied`() = withCheckpointStorage { store ->
        val a = StoredCheckpoint("projection", "1", "1", Clock.System.now(), CheckpointBucket())
        store.storeCheckpoint(a)
        val b = StoredCheckpoint("projection", "2", "2", Clock.System.now(), CheckpointBucket())
        store.storeCheckpoint(b)
        val retrieved = store.getLatestCheckpoint("projection")
        assertNotNull(retrieved)
        assertEquals(retrieved.version, b.version)
    }

    @Test
    fun `Clear checkpoints does actually remove all checkpoints`() = withCheckpointStorage { store ->
        val input = StoredCheckpoint("projection", "1", "1", Clock.System.now(), CheckpointBucket())
        store.storeCheckpoint(input)
        store.clearCheckpoints()
        val retrieved = store.getLatestCheckpoint("projection")
        assertNull(retrieved)
    }
}