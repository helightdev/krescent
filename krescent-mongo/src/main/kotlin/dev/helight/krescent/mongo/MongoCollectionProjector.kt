package dev.helight.krescent.mongo

import com.mongodb.MongoNamespace
import com.mongodb.client.model.RenameCollectionOptions
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import dev.helight.krescent.checkpoint.CheckpointBucket
import dev.helight.krescent.checkpoint.CheckpointSupport
import dev.helight.krescent.event.Event
import dev.helight.krescent.event.EventStreamProcessor
import dev.helight.krescent.event.SystemStreamHeadEvent
import dev.helight.krescent.model.ExtensionAwareBuilder
import dev.helight.krescent.model.ModelExtension
import kotlinx.coroutines.flow.count
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.bson.Document

class MongoCollectionProjector(
    val name: String,
    val database: MongoDatabase,
    val checkpointCollectionName: String = "$name-checkpoint",
) : ModelExtension<MongoCollection<Document>>, EventStreamProcessor, CheckpointSupport {

    val collection: MongoCollection<Document>
        get() {
            return database.getCollection<Document>(name)
        }

    override fun unpack(): MongoCollection<Document> = collection

    override suspend fun process(event: Event) {
        if (event is SystemStreamHeadEvent) {
            collection.drop()
        }
    }

    private suspend fun loadFromCollection(sourceCollection: String) {
        val fromCollection = database.getCollection<Document>(sourceCollection)
        fromCollection.renameCollection(
            MongoNamespace(
                database.name,
                name
            ), RenameCollectionOptions().dropTarget(true)
        )
        exportToCollection(sourceCollection)
    }

    private suspend fun exportToCollection(targetName: String): Int {
        database.getCollection<Document>(targetName).drop()
        val documentCount = collection.aggregate(
            listOf(
                Document("\$match", Document()),
                Document("\$out", targetName),
            )
        ).count()
        return documentCount
    }

    override suspend fun createCheckpoint(bucket: CheckpointBucket) {
        val collectionSize = exportToCollection(checkpointCollectionName)
        bucket[name] = Json.encodeToJsonElement(
            SnapshotData(
                collection = checkpointCollectionName,
                size = collectionSize,
            )
        )
    }

    override suspend fun restoreCheckpoint(bucket: CheckpointBucket) {
        val (collection, size) = Json.decodeFromJsonElement<SnapshotData>(bucket[name]!!)
        if (collection != checkpointCollectionName) error("Checkpoint collection name mismatch, expected $checkpointCollectionName but got $collection")
        val actualSize = database.getCollection<Document>(collection).countDocuments().toInt()
        if (actualSize != size) error("Checkpoint collection size mismatch, expected $size but got $actualSize")
        loadFromCollection(checkpointCollectionName)
    }

    @Serializable
    data class SnapshotData(
        val collection: String,
        val size: Int,
    )

    companion object {
        @Suppress("unused")
        fun ExtensionAwareBuilder.mongoCollectionProjector(
            name: String,
            database: MongoDatabase,
            checkpointCollectionName: String = "$name-checkpoint",
        ): MongoCollectionProjector {
            return registerExtension(MongoCollectionProjector(name, database, checkpointCollectionName))
        }
    }
}

