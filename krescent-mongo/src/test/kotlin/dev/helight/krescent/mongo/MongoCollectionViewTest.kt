package dev.helight.krescent.mongo

import com.mongodb.kotlin.client.coroutine.MongoClient
import dev.helight.krescent.checkpoints.CheckpointBucket
import kotlinx.coroutines.runBlocking
import org.bson.Document
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration

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
        val view = MongoCollectionView("MyReadModel", database)
        view.collection.drop()
        view.collection.insertOne(Document().apply {
            put("name", "test")
        })
        assertEquals(1, view.collection.countDocuments())
        val bucket = CheckpointBucket(mutableMapOf())
        view.createCheckpoint(bucket)
        assertEquals(1, view.collection.countDocuments())

        view.collection.insertOne(Document().apply {
            put("name", "test2")
        })
        assertEquals(2, view.collection.countDocuments())

        view.restoreCheckpoint(bucket)
        assertEquals(1, view.collection.countDocuments())
    }

}