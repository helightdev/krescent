package dev.helight.krescent.exposed

import dev.helight.krescent.event.EventMessage
import dev.helight.krescent.source.EventPublisher
import dev.helight.krescent.source.StreamingEventSource
import dev.helight.krescent.test.StreamingEventSourceContract
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.exposed.sql.Database
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

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

    fun execWithTable(block: suspend CoroutineScope.(Database, KrescentTable) -> Unit) = runBlocking {
        val db = connect()
        val tableName = "krescent_${UUID.randomUUID()}"
        val table = KrescentTable(tableName)
        table.create(db)
        try {
            this.block(db, table)
            delay(300)
        } finally {
            runCatching { table.drop(db) }
        }
    }

    override fun execWithStreamingSource(block: suspend CoroutineScope.(StreamingEventSource, EventPublisher) -> Unit) =
        execWithTable { db, table ->
            val source = StreamingExposedEventSource(table, db, pollingDelay = 100L)
            val publisher = ExposedEventPublisher(table, db, "default")
            this.block(source, publisher)
        }


    @Test
    fun `Like matching event stream`() = execWithTable { db, table ->
        ExposedEventPublisher(table, db, "user-ALICE-conversation-1")
            .publish(EventMessage(type = "created", payload = buildJsonObject { put("number", 1) }))

        ExposedEventPublisher(table, db, "user-BOB-conversation-1")
            .publish(EventMessage(type = "created", payload = buildJsonObject { put("number", 2) }))

        ExposedEventPublisher(table, db, "user-ALICE-conversation-2")
            .publish(EventMessage(type = "created", payload = buildJsonObject { put("number", 3) }))

        val aliceEvents = StreamingExposedEventSource(table, db, "user-ALICE-conversation-%", StreamIdMatcher.LIKE)
            .fetchEventsAfter().toList()
        val bobEvents = StreamingExposedEventSource(table, db, "user-BOB-conversation-%", StreamIdMatcher.LIKE)
            .fetchEventsAfter().toList()

        assertEquals(2, aliceEvents.size)
        assertEquals(1, bobEvents.size)
    }

    @Test
    fun `Regex matching event stream`() = execWithTable { db, table ->
        ExposedEventPublisher(table, db, "user-ALICE-conversation-1")
            .publish(EventMessage(type = "created", payload = buildJsonObject { put("number", 1) }))

        ExposedEventPublisher(table, db, "user-BOB-conversation-1")
            .publish(EventMessage(type = "created", payload = buildJsonObject { put("number", 2) }))

        ExposedEventPublisher(table, db, "user-ALICE-conversation-2")
            .publish(EventMessage(type = "created", payload = buildJsonObject { put("number", 3) }))

        val aliceEvents = StreamingExposedEventSource(table, db, "^user-ALICE-conversation-.*", StreamIdMatcher.REGEX)
            .fetchEventsAfter().toList()
        val bobEvents = StreamingExposedEventSource(table, db, "^user-BOB-conversation-.*", StreamIdMatcher.REGEX)
            .fetchEventsAfter().toList()

        assertEquals(2, aliceEvents.size)
        assertEquals(1, bobEvents.size)
    }

}