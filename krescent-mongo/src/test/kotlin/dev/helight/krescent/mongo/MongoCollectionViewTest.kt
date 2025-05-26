package dev.helight.krescent.mongo

import com.mongodb.kotlin.client.coroutine.MongoClient
import dev.helight.krescent.checkpoint.CheckpointBucket
import dev.helight.krescent.checkpoint.StoredCheckpoint
import kotlinx.coroutines.runBlocking
import org.bson.Document
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration
import java.time.Instant
import kotlin.test.assertNull

@Testcontainers
class MongoCollectionViewTest {

    companion object {

        @Container
        private val mongo = GenericContainer("mongo:latest")
            .withExposedPorts(27017)
            .withEnv("MONGO_INITDB_ROOT_USERNAME", "root")
            .withEnv("MONGO_INITDB_ROOT_PASSWORD", "example")
            .withStartupTimeout(Duration.ofSeconds(60))

        private val connectionString get() = "mongodb://root:example@localhost:${mongo.getMappedPort(27017)}/"
    }

    @Test
    fun `Create and load checkpoints`() = runBlocking {
        val client = MongoClient.create(connectionString)
        val database = client.getDatabase("test")
        val view = MongoCollectionProjector("MyReadModel", database)
        view.drop()
        view.insertOne(Document().apply {
            put("name", "test")
        })
        assertEquals(1, view.lease { countDocuments() })
        val bucket = CheckpointBucket(mutableMapOf())
        view.createCheckpoint(bucket)
        assertEquals(1, view.lease { countDocuments() })

        view.insertOne(Document().apply {
            put("name", "test2")
        })
        assertEquals(2, view.lease { countDocuments() })

        view.restoreCheckpoint(bucket)
        assertEquals(1, view.lease { countDocuments() })
    }


    @Test
    fun `Test Checkpoint Storage`() = runBlocking {
        val client = MongoClient.create(connectionString)
        val database = client.getDatabase("test")
        val storage = MongoCheckpointStorage(database)

        val testCheckpointNS = "test-namespace"
        assertNull(storage.getLatestCheckpoint(testCheckpointNS))

        val checkpoint = StoredCheckpoint(
            namespace = testCheckpointNS,
            version = "1",
            position = "test-position",
            timestamp = Instant.now(),
            data = CheckpointBucket()
        )

        storage.storeCheckpoint(checkpoint)
        val loadedCheckpoint = storage.getLatestCheckpoint(testCheckpointNS)
        assertEquals(checkpoint.namespace, loadedCheckpoint?.namespace)
        assertEquals("1", loadedCheckpoint?.version)

        val updatedCheckpoint = StoredCheckpoint(
            namespace = testCheckpointNS,
            version = "2",
            position = "updated-position",
            timestamp = Instant.now(),
            data = CheckpointBucket()
        )
        storage.storeCheckpoint(updatedCheckpoint)

        val reloadedCheckpoint = storage.getLatestCheckpoint(testCheckpointNS)
        assertEquals(checkpoint.namespace, reloadedCheckpoint?.namespace)
        assertEquals("2", reloadedCheckpoint?.version)

        storage.clearCheckpoints()
        assertNull(storage.getLatestCheckpoint(testCheckpointNS))
    }

}