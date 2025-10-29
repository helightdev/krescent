package dev.helight.krescent.exposed

import dev.helight.krescent.checkpoint.CheckpointBucket
import dev.helight.krescent.checkpoint.CheckpointStorage
import dev.helight.krescent.checkpoint.StoredCheckpoint
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

class ExposedCheckpointStorage(
    val database: Database,
    val table: KrescentCheckpointTable = KrescentCheckpointTable(),
) : CheckpointStorage {

    suspend fun ensureCreated() {
        table.create(database)
    }

    override suspend fun storeCheckpoint(checkpoint: StoredCheckpoint): Unit = jdbcSuspendTransaction(database) {
        table.upsert(keys = arrayOf(table.namespace)) {
            it[namespace] = checkpoint.namespace
            it[position] = checkpoint.position
            it[timestamp] = checkpoint.timestamp
            it[version] = checkpoint.version
            it[data] = checkpoint.data.encodeToByteArray()
        }
    }

    override suspend fun getLatestCheckpoint(namespace: String): StoredCheckpoint? = jdbcSuspendTransaction(database) {
        table.selectAll().where { table.namespace eq namespace }.firstOrNull()?.let {
            StoredCheckpoint(
                namespace = it[table.namespace],
                position = it[table.position],
                version = it[table.version],
                timestamp = it[table.timestamp],
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