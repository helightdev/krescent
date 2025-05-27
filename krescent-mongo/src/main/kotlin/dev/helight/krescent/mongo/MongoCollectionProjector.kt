package dev.helight.krescent.mongo

import com.mongodb.MongoNamespace
import com.mongodb.client.model.*
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import dev.helight.krescent.checkpoint.CheckpointBucket
import dev.helight.krescent.checkpoint.CheckpointSupport
import dev.helight.krescent.event.*
import dev.helight.krescent.model.ExtensionAwareBuilder
import dev.helight.krescent.model.ModelExtension
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.bson.Document
import org.bson.conversions.Bson

/**
 * A MongoDB collection projector that supports batching and checkpointing.
 */
class MongoCollectionProjector(
    val name: String,
    val database: MongoDatabase,
    val checkpointCollectionName: String = "$name-checkpoint",
    val allowBatching: Boolean = false,
    val maxBatchSize: Int = 100,
) : ModelExtension<MongoCollectionProjector>, EventStreamProcessor, CheckpointSupport {

    private val collection: MongoCollection<Document>
            by lazy { database.getCollection<Document>(name) }

    private var isBatching = false
    private val batchBuffer = mutableListOf<WriteModel<Document>>()
    private val mutex = Mutex()

    override fun unpack(): MongoCollectionProjector = this

    override suspend fun process(event: Event) = when {
        event is SystemStreamHeadEvent -> collection.drop()
        event is SystemStreamCatchUpEvent && allowBatching -> {
            isBatching = true
        }
        event is SystemStreamCaughtUpEvent && isBatching -> {
            isBatching = false
            unsafeCommitBatch()
        }
        else -> {}
    }

    private suspend fun unsafeCommitBatch() {
        if (batchBuffer.isEmpty()) return
        collection.bulkWrite(batchBuffer.toList(), BulkWriteOptions().ordered(true))
        batchBuffer.clear()
    }

    private suspend fun addToBatch(model: WriteModel<Document>) {
        batchBuffer.add(model)
        val hasReachedMaxSize = batchBuffer.size >= maxBatchSize
        if (hasReachedMaxSize) unsafeCommitBatch()
    }

    suspend fun insertOne(document: Document) = mutex.withLock {
        if (isBatching) addToBatch(InsertOneModel(document))
        else collection.insertOne(document)
    }

    suspend fun updateOne(filter: Bson, update: Bson) = mutex.withLock {
        if (isBatching) addToBatch(UpdateOneModel(filter, update))
        else collection.updateOne(filter, update)
    }

    suspend fun updateMany(filter: Bson, update: Bson) = mutex.withLock {
        if (isBatching) addToBatch(UpdateManyModel(filter, update))
        else collection.updateMany(filter, update)
    }

    suspend fun deleteOne(filter: Bson) = mutex.withLock {
        if (isBatching) addToBatch(DeleteOneModel(filter))
        else collection.deleteOne(filter)
    }

    suspend fun drop() = mutex.withLock {
        batchBuffer.clear()
        collection.drop()
    }

    suspend fun <R> lease(block: suspend MongoCollection<Document>.() -> R): R = mutex.withLock {
        unsafeCommitBatch()
        block(collection)
    }

    suspend fun commitBatch() = mutex.withLock {
        unsafeCommitBatch()
    }

    private suspend fun loadFromCollection(sourceCollection: String) = mutex.withLock {
        val fromCollection = database.getCollection<Document>(sourceCollection)
        fromCollection.renameCollection(
            MongoNamespace(
                database.name,
                name
            ), RenameCollectionOptions().dropTarget(true)
        )
        exportToCollection(sourceCollection)
    }

    private suspend fun exportToCollection(targetName: String): Int = mutex.withLock {
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

        /**
         * Creates a [MongoCollectionProjector] and registers it with the [ExtensionAwareBuilder].
         *
         * [dev.helight.krescent.mongo.MongoCollectionProjector] is a wrapper around a MongoDB collection that supports
         * batching and checkpointing. Checkpoints are created by exporting the collection to a separate checkpoint
         * collection, which can be restored later by overriding the working collection using a dropping rename operation.
         *
         * By default, write operations are not batched since it may introduce additional points of failure but
         * can be turned on when write performance is creating a bottleneck.
         *
         * @param name The name of the projector, which is also used as the collection name in MongoDB.
         * @param database The MongoDB database to use.
         * @param checkpointCollectionName The name of the checkpoint collection, defaults to `"$name-checkpoint"`.
         * @param allowBatching Whether to allow batching of events, defaults to false.
         * @param maxBatchSize The maximum size of a batch, defaults to 100.
         */
        fun ExtensionAwareBuilder.mongoCollectionProjection(
            name: String,
            database: MongoDatabase,
            checkpointCollectionName: String = "$name-checkpoint",
            allowBatching: Boolean = false,
            maxBatchSize: Int = 100,
        ): MongoCollectionProjector = registerExtension(
            MongoCollectionProjector(
                name, database, checkpointCollectionName,
                allowBatching, maxBatchSize
            )
        )
    }
}

