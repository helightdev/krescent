package dev.helight.krescent.mongo

import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import dev.helight.krescent.checkpoint.CheckpointStorage
import dev.helight.krescent.checkpoint.StoredCheckpoint
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.bson.Document

class MongoCheckpointStorage(
    val database: MongoDatabase,
    collectionName: String = "checkpoints",
) : CheckpointStorage {

    private val collection = database.getCollection<Document>(collectionName)

    override suspend fun storeCheckpoint(checkpoint: StoredCheckpoint) {
        val document = checkpointToDocument(checkpoint)
        collection.replaceOne(
            Filters.eq("_id", checkpoint.namespace),
            document,
            ReplaceOptions().upsert(true)
        )
    }

    override suspend fun getLatestCheckpoint(namespace: String): StoredCheckpoint? {
        val document = collection.find(Filters.eq("_id", namespace)).firstOrNull() ?: return null
        return documentToCheckpoint(document)
    }

    override suspend fun clearCheckpoints() {
        collection.drop()
    }

    private fun checkpointToDocument(checkpoint: StoredCheckpoint): Document {
        return Document().apply {
            put("_id", checkpoint.namespace)
            put("revision", checkpoint.revision)
            put("position", checkpoint.position)
            put("timestamp", checkpoint.timestamp)
            put("data", Json.Default.encodeToString(checkpoint.data))
        }
    }

    private fun documentToCheckpoint(document: Document): StoredCheckpoint {
        return StoredCheckpoint(
            namespace = document.getString("_id"),
            revision = document.getInteger("revision"),
            position = document.getString("position"),
            timestamp = document.getDate("timestamp").toInstant(),
            data = Json.Default.decodeFromString(document.getString("data")!!),
        )
    }
}