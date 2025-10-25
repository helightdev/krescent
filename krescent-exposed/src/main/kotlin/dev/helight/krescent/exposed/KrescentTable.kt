package dev.helight.krescent.exposed

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.json.json
import org.jetbrains.exposed.sql.kotlin.datetime.KotlinInstantColumnType
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class KrescentTable(tableName: String = "krescent") : LongIdTable(tableName) {
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
        jdbcSuspendTransaction(database) { SchemaUtils.create(this@KrescentTable) }
    }

    suspend fun drop(database: Database) {
        jdbcSuspendTransaction(database) { SchemaUtils.drop(this@KrescentTable) }
    }
}


