@file:Suppress("ExposedReference")

package dev.helight.krescent.exposed

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.datetime.KotlinInstantColumnType
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.upsert
import org.jetbrains.exposed.v1.json.json
import org.jetbrains.exposed.v1.json.jsonb
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalTime::class)
class KrescentEventLogTable(tableName: String = "krescent") : LongIdTable(tableName) {
    @OptIn(ExperimentalUuidApi::class)
    val uid = uuid("uuid").uniqueIndex()
    val streamId = text("streamId").index()
    val type = text("type").index()
    val timestamp = timestamp("timestamp").index()

    val data = jsonb("data", {
        Json.encodeToString(it)
    }, {
        Json.decodeFromString(JsonElement.serializer(), it)
    }).index()

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
    val data = binary("data")

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

