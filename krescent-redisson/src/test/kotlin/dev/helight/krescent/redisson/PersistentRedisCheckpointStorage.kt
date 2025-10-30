package dev.helight.krescent.redisson

import dev.helight.krescent.checkpoint.CheckpointStorage
import dev.helight.krescent.test.CheckpointStoreContract
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration
import java.util.*

@Testcontainers
class PersistentRedisCheckpointStorageTest : CheckpointStoreContract {

    companion object {

        @Container
        private val valkey = GenericContainer("valkey/valkey:latest")
            .withExposedPorts(6379)
            .withStartupTimeout(Duration.ofSeconds(60))

        private val connectionString get() = "redis://${valkey.host}:${valkey.getMappedPort(6379)}"

        fun connect(): RedissonClient = Redisson.create(Config().apply {
            useSingleServer().setAddress(connectionString)
        })
    }

    override fun withCheckpointStorage(block: suspend CoroutineScope.(CheckpointStorage) -> Unit) = runBlocking {
        val db = connect()
        val mapName = "storage:${UUID.randomUUID()}"
        val storage = PersistentRedisCheckpointStorage(db, mapName)
        try {
            this.block(storage)
            delay(300)
        } finally {
            storage.clearCheckpoints()
        }
    }
}