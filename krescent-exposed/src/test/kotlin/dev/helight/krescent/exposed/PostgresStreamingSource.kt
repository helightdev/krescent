package dev.helight.krescent.exposed

import dev.helight.krescent.source.EventPublisher
import dev.helight.krescent.source.StreamingEventSource
import dev.helight.krescent.test.StreamingEventSourceContract
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration
import java.util.*

@Testcontainers
class PostgresStreamingSource : StreamingEventSourceContract {

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

    override fun execWithStreamingSource(block: suspend CoroutineScope.(StreamingEventSource, EventPublisher) -> Unit) =
        runBlocking {
            val db = connect()
            val tableName = "krescent_${UUID.randomUUID()}"
            val table = KrescentTable(tableName)
            table.create(db)
            val source = StreamingExposedEventSource(table, db, pollingDelay = 100L)
            val publisher = ExposedEventPublisher(table, db, "default")
            try {
                this.block(source, publisher)
            } finally {
                runCatching { table.drop(db) }
            }
            delay(300)
        }

}