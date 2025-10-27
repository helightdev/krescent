@file:Suppress("ExposedReference")

package dev.helight.krescent.exposed

import dev.helight.krescent.checkpoint.CheckpointBucket
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.json.json
import org.jetbrains.exposed.sql.kotlin.datetime.KotlinInstantColumnType
import org.jetbrains.exposed.sql.upsert
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class KrescentEventLogTable(tableName: String = "krescent") : LongIdTable(tableName) {
    val uid = uuid("uuid").uniqueIndex()
    val streamId = text("streamId").index()
    val type = text("type").index()
    val timestamp = registerColumn("timestamp", KotlinInstantColumnType()).index()

    val data = json("data", {
        Json.encodeToString(it)
    }, {
        Json.decodeFromString(JsonElement.serializer(), it)
    })

    suspend fun create(database: Database) {
        jdbcSuspendTransaction(database) { SchemaUtils.create(this@KrescentEventLogTable) }
    }

    suspend fun drop(database: Database) {
        jdbcSuspendTransaction(database) { SchemaUtils.drop(this@KrescentEventLogTable) }
    }
}

@OptIn(ExperimentalTime::class)
class KrescentCheckpointTable(name: String = "krescent_checkpoints") : Table(name) {
    val namespace = text("namespace").uniqueIndex()
    val version = text("version")
    val position = text("position")
    val timestamp = registerColumn("timestamp", KotlinInstantColumnType()).index()
    val data = json(
        name = "data",
        serialize = { it.encodeToJsonString() },
        deserialize = { CheckpointBucket.fromJsonString(it) }
    )

    override val primaryKey = PrimaryKey(namespace)

    suspend fun create(database: Database) {
        jdbcSuspendTransaction(database) { SchemaUtils.create(this@KrescentCheckpointTable) }
    }

    suspend fun drop(database: Database) {
        jdbcSuspendTransaction(database) { SchemaUtils.drop(this@KrescentCheckpointTable) }
    }
}

class KrescentProjectionMetadataTable(name: String) : Table(name) {
    val key = text("key").uniqueIndex()
    val value = json("value", { it }, { it })
    override val primaryKey = PrimaryKey(key)

    inline fun <reified T> get(key: String): T? {
        val value = select(value)
            .where { this@KrescentProjectionMetadataTable.key eq key }
            .firstOrNull()?.get(value) ?: return null
        return value.runCatching { Json.decodeFromString<T>(this) }.getOrNull()
    }

    inline fun <reified T> set(key: String, value: T) {
        val value = Json.encodeToString(value)
        upsert(keys = arrayOf(this.key)) {
            it[this.key] = key
            it[this.value] = value
        }
    }

}

