package dev.helight.krescent.exposed

import dev.helight.krescent.checkpoint.CheckpointBucket
import dev.helight.krescent.checkpoint.CheckpointStorage
import dev.helight.krescent.checkpoint.StoredCheckpoint
import kotlinx.datetime.toDeprecatedInstant
import kotlinx.datetime.toStdlibInstant
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.*
import kotlin.time.ExperimentalTime

class ExposedCheckpointStorage(
    val database: Database,
    val table: KrescentCheckpointTable = KrescentCheckpointTable(),
) : CheckpointStorage {

    suspend fun ensureCreated() {
        table.create(database)
    }

    @OptIn(ExperimentalTime::class)
    override suspend fun storeCheckpoint(checkpoint: StoredCheckpoint): Unit = jdbcSuspendTransaction(database) {
        table.upsert(keys = arrayOf(table.namespace)) {
            it[namespace] = checkpoint.namespace
            it[position] = checkpoint.position
            it[timestamp] = checkpoint.timestamp.toStdlibInstant()
            it[version] = checkpoint.version
            it[data] = checkpoint.data.encodeToByteArray()
        }
    }

    @OptIn(ExperimentalTime::class)
    override suspend fun getLatestCheckpoint(namespace: String): StoredCheckpoint? = jdbcSuspendTransaction(database) {
        table.selectAll().where { table.namespace eq namespace }.firstOrNull()?.let {
            StoredCheckpoint(
                namespace = it[table.namespace],
                position = it[table.position],
                version = it[table.version],
                timestamp = it[table.timestamp].toDeprecatedInstant(),
                data = CheckpointBucket.fromByteArray(it[table.data])
            )
        }
    }

    override suspend fun deleteCheckpoint(namespace: String): Unit = jdbcSuspendTransaction(database) {
        table.deleteWhere(1) { table.namespace eq namespace }
    }

    override suspend fun clearCheckpoints(): Unit = jdbcSuspendTransaction(database) {
        table.deleteAll()
    }
}