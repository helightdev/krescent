package dev.helight.krescent.benchmark

import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import dev.helight.krescent.event.Event
import dev.helight.krescent.event.buildEventCatalog
import dev.helight.krescent.model.ReadModelBase
import dev.helight.krescent.model.projection.MemoryMapBuffer
import dev.helight.krescent.model.projection.StateMemoryProjector.Companion.mapMemoryProjection
import dev.helight.krescent.mongo.MongoCollectionProjector.Companion.mongoCollectionProjection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import org.bson.Document
import java.util.*

@Serializable
class DocumentCreatedEvent(
    val documentId: String,
) : Event()

@Serializable
class DocumentFieldSetEvent(
    val documentId: String,
    val fieldName: String,
    val fieldValue: String,
) : Event()

@Serializable
class DocumentFieldRemovedEvent(
    val documentId: String,
    val fieldName: String,
) : Event()

@Serializable
class DocumentDeletedEvent(
    val documentId: String,
) : Event()

val documentEventCatalog = buildEventCatalog(1) {
    event<DocumentCreatedEvent>("document.created")
    event<DocumentFieldSetEvent>("document.field.set")
    event<DocumentFieldRemovedEvent>("document.field.removed")
    event<DocumentDeletedEvent>("document.deleted")
}

fun produceMockEventStream(length: Int): Flow<Event> {
    val possibleFields = listOf("title", "author", "content", "tags", "summary")
    val possibleValues = listOf(
        "Lorem ipsum dolor sit amet",
        "Consectetur adipiscing elit",
        "Sed do eiusmod tempor incididunt",
        "Ut labore et dolore magna aliqua",
        "Excepteur sint occaecat cupidatat non proident"
    )
    val existing = mutableMapOf<String, MutableSet<String>>()
    val random = Random()
    return flow {
        repeat(length) {
            while (true) {
                val operation = random.nextInt(4)
                when (operation) {
                    0 -> { // Create document
                        val documentId = UUID.randomUUID().toString()
                        emit(DocumentCreatedEvent(documentId))
                        existing[documentId] = mutableSetOf()
                        break
                    }

                    1 -> { // Set field
                        val documentId = existing.keys.randomOrNull() ?: continue
                        val fieldName = possibleFields.random()
                        val fieldValue = possibleValues.random()
                        emit(DocumentFieldSetEvent(documentId, fieldName, fieldValue))
                        existing[documentId]?.add(fieldName)
                        break
                    }

                    2 -> { // Remove field
                        val documentId = existing.keys.randomOrNull() ?: continue
                        val fieldName = existing[documentId]?.randomOrNull() ?: continue
                        emit(DocumentFieldRemovedEvent(documentId, fieldName))
                        existing[documentId]?.remove(fieldName)
                        break
                    }

                    3 -> { // Delete document
                        val documentId = existing.keys.randomOrNull() ?: continue
                        emit(DocumentDeletedEvent(documentId))
                        existing.remove(documentId)
                        break
                    }
                }
            }
        }
    }
}


class AllDocumentsReadModel(
    buffer: MemoryMapBuffer<String, MutableMap<String, String>> = MemoryMapBuffer(),
) : ReadModelBase(
    "documents.all-documents", 1, documentEventCatalog,
) {

    val projection by mapMemoryProjection<String, MutableMap<String, String>>("buffer", buffer)

    override suspend fun process(event: Event) {
        when (event) {
            is DocumentCreatedEvent -> {
                projection[event.documentId] = mutableMapOf()
            }

            is DocumentFieldSetEvent -> {
                projection[event.documentId]?.set(event.fieldName, event.fieldValue)
            }

            is DocumentFieldRemovedEvent -> {
                projection[event.documentId]?.remove(event.fieldName)
            }

            is DocumentDeletedEvent -> {
                projection.remove(event.documentId)
            }
        }
    }
}

class AllDocumentsReadModelMongo(
    val collectionName: String,
    val database: MongoDatabase,
) : ReadModelBase(
    "documents.all-documents", 1, documentEventCatalog,
) {

    val projection by mongoCollectionProjection(collectionName, database, allowBatching = true)

    override suspend fun process(event: Event) {
        when (event) {
            is DocumentCreatedEvent -> {
                projection.insertOne(Document().apply {
                    put("_id", event.documentId)
                })
            }

            is DocumentFieldSetEvent -> {
                projection.updateOne(
                    Document("_id", event.documentId),
                    Updates.set(event.fieldName, event.fieldValue)
                )
            }

            is DocumentFieldRemovedEvent -> {
                projection.updateOne(
                    Document("_id", event.documentId),
                    Updates.unset(event.fieldName)
                )
            }

            is DocumentDeletedEvent -> {
                projection.deleteOne(
                    Document("_id", event.documentId)
                )
            }
        }
    }
}