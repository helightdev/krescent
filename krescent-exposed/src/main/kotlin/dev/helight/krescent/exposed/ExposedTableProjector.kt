@file:Suppress("ExposedReference")

package dev.helight.krescent.exposed

import dev.helight.krescent.checkpoint.CheckpointBucket
import dev.helight.krescent.checkpoint.CheckpointSupport
import dev.helight.krescent.event.Event
import dev.helight.krescent.event.EventStreamProcessor
import dev.helight.krescent.event.SystemStreamHeadEvent
import dev.helight.krescent.model.ExtensionAwareBuilder
import dev.helight.krescent.model.ModelExtension
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.jetbrains.exposed.sql.*
import java.util.logging.Logger

class ExposedTableProjector(
    val database: Database,
    val table: Table,
    val name: String,
    val metadataTable: KrescentProjectionMetadataTable,
) : ModelExtension<ExposedTableProjector>, EventStreamProcessor, CheckpointSupport {

    private val logger: Logger = Logger.getLogger("ExposedTableProjector-${name}")
    private var revision: Int = 0

    override fun unpack(): ExposedTableProjector = this

    override suspend fun process(event: Event) = when {
        event is SystemStreamHeadEvent -> {
            reset()
        }

        else -> {}
    }

    private suspend fun reset() = jdbcSuspendTransaction(database) {
        SchemaUtils.drop(table, metadataTable)
        SchemaUtils.create(table, metadataTable)
        revision = 0
        metadataTable.set<Int>("revision", revision)
    }

    override suspend fun createCheckpoint(bucket: CheckpointBucket) {
        bucket[name] = Json.encodeToJsonElement(SnapshotData(revision))
    }

    override suspend fun restoreCheckpoint(bucket: CheckpointBucket) {
        val snapshot = Json.decodeFromJsonElement<SnapshotData>(bucket[name]!!)
        revision = snapshot.revision
    }

    /**
     * Executes a read-only transaction within the given database.
     *
     * @param block The transactional logic to be executed. This is a suspending lambda with the [Transaction] context.
     * @return The result of the executed transactional block of type [R].
     */
    suspend fun <R> readTransaction(block: suspend Transaction.() -> R): R = jdbcSuspendTransaction(database) {
        block()
    }

    /**
     * Executes a write transaction within the given database, ensuring that changes are committed
     * and a revision number is incremented and stored in the metadata table.
     *
     * @param block The transactional logic to be executed. This is a suspending lambda with the [Transaction] context.
     * @return The result of the executed transactional block of type [R].
     */
    suspend fun <R> writeTransaction(block: suspend Transaction.() -> R): R = jdbcSuspendTransaction(database) {
        val returned = block()
        if (revision++ == Int.MAX_VALUE) revision = 0
        metadataTable.set<Int>("revision", revision)
        returned
    }

    override suspend fun validateCheckpoint(bucket: CheckpointBucket): Boolean =
        jdbcSuspendTransaction(database) inner@{
            if (!table.exists() || !metadataTable.exists()) {
                logger.info("Tried resuming from a checkpoint, but projection tables were missing")
                return@inner false
            }
            val snapshot = bucket[name]?.runCatching { Json.decodeFromJsonElement<SnapshotData>(this) }?.getOrNull()
            if (snapshot == null) {
                logger.info("Tried resuming from a checkpoint, but no snapshot data was found in the checkpoint")
                return@inner false
            }
            val revision = metadataTable.get<Int>("revision")
            if (revision == null) {
                logger.info("Tried resuming from a checkpoint, but no revision was found in the metadata")
                return@inner false
            }
            if (snapshot.revision != revision) {
                logger.info("Tried resuming from a checkpoint, but the projection revision didn't match the expected revision")
                return@inner false
            }
            true
        }

    @Serializable
    private data class SnapshotData(
        val revision: Int,
    )

    companion object {

        fun ExtensionAwareBuilder.exposedTableProjection(
            database: Database,
            table: Table,
            name: String = table.tableName,
            metadataTable: KrescentProjectionMetadataTable = KrescentProjectionMetadataTable("${table.tableName}_metadata"),
        ): ExposedTableProjector = registerExtension(
            ExposedTableProjector(
                database = database,
                table = table,
                name = name,
                metadataTable = metadataTable,
            )
        )
    }
}