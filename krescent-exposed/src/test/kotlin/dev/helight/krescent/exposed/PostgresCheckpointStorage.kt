package dev.helight.krescent.exposed

import dev.helight.krescent.checkpoint.CheckpointStorage
import dev.helight.krescent.test.CheckpointStoreContract
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration
import java.util.*

@Testcontainers
class PostgresCheckpointStorage : CheckpointStoreContract {

    companion object {

        @Container
        private val postgres = GenericContainer("postgres:latest")
            .withExposedPorts(5432)
            .withEnv("POSTGRES_USER", "root")
            .withEnv("POSTGRES_PASSWORD", "example")
            .withStartupTimeout(Duration.ofSeconds(60))

        private val connectionString get() = "jdbc:postgresql://${postgres.host}:${postgres.getMappedPort(5432)}/root"

        fun connect(): Database = Database.connect(
            url = connectionString,
            driver = "org.postgresql.Driver",
            user = "root",
            password = "example"
        )
    }

    override fun withCheckpointStorage(block: suspend CoroutineScope.(CheckpointStorage) -> Unit) = runBlocking {
        val db = connect()
        val tableName = "krescent_storage_${UUID.randomUUID()}"
        val table = KrescentCheckpointTable(tableName)
        table.create(db)
        try {
            val storage = ExposedCheckpointStorage(db, table)
            this.block(storage)
            delay(300)
        } finally {
            runCatching { table.drop(db) }
        }
    }
}