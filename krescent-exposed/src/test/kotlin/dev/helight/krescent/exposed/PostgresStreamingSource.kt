package dev.helight.krescent.exposed

import dev.helight.krescent.event.Event
import dev.helight.krescent.event.EventMessage
import dev.helight.krescent.event.buildEventCatalog
import dev.helight.krescent.source.EventPublisher
import dev.helight.krescent.source.PollingStreamingEventSource.Companion.polling
import dev.helight.krescent.source.StreamingEventSource
import dev.helight.krescent.test.StreamingEventSourceContract
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
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

    fun execWithTable(block: suspend CoroutineScope.(Database, KrescentEventLogTable) -> Unit) = runBlocking {
        val db = connect()
        val tableName = "krescent_${UUID.randomUUID()}"
        val table = KrescentEventLogTable(tableName)
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
            val source = ExposedEventSource(db, table = table).polling()
            val publisher = ExposedEventPublisher(db, "default", table)
            this.block(source, publisher)
        }


    @Test
    fun `Like matching event stream`() = execWithTable { db, table ->
        ExposedEventPublisher(db, "user-ALICE-conversation-1", table)
            .publish(EventMessage(type = "created", payload = buildJsonObject { put("number", 1) }))

        ExposedEventPublisher(db, "user-BOB-conversation-1", table)
            .publish(EventMessage(type = "created", payload = buildJsonObject { put("number", 2) }))

        ExposedEventPublisher(db, "user-ALICE-conversation-2", table)
            .publish(EventMessage(type = "created", payload = buildJsonObject { put("number", 3) }))

        val aliceEvents = ExposedEventSource(db, "user-ALICE-conversation-%", table, StreamIdMatcher.LIKE)
            .fetchEventsAfter().toList()
        val bobEvents = ExposedEventSource(db, "user-BOB-conversation-%", table, StreamIdMatcher.LIKE)
            .fetchEventsAfter().toList()

        assertEquals(2, aliceEvents.size)
        assertEquals(1, bobEvents.size)
    }

    @Test
    fun `Regex matching event stream`() = execWithTable { db, table ->
        ExposedEventPublisher(db, "user-ALICE-conversation-1", table)
            .publish(EventMessage(type = "created", payload = buildJsonObject { put("number", 1) }))

        ExposedEventPublisher(db, "user-BOB-conversation-1", table)
            .publish(EventMessage(type = "created", payload = buildJsonObject { put("number", 2) }))

        ExposedEventPublisher(db, "user-ALICE-conversation-2", table)
            .publish(EventMessage(type = "created", payload = buildJsonObject { put("number", 3) }))

        val aliceEvents = ExposedEventSource(db, "^user-ALICE-conversation-.*", table, StreamIdMatcher.REGEX)
            .fetchEventsAfter().toList()
        val bobEvents = ExposedEventSource(db, "^user-BOB-conversation-.*", table, StreamIdMatcher.REGEX)
            .fetchEventsAfter().toList()

        assertEquals(2, aliceEvents.size)
        assertEquals(1, bobEvents.size)
    }

    @Test
    fun `Simple event filter using the regex`() = execWithTable { db, table ->
        val publisher = ExposedEventPublisher(db, "event-stream", table)
        publisher.publish(exampleCatalog.create(EventA(1)))
        publisher.publish(exampleCatalog.create(EventB(2)))
        publisher.publish(exampleCatalog.create(EventC(3)))
        publisher.publish(exampleCatalog.create(EventC(4)))

        val regexReceived = ExposedEventSource(
            db, "event-stream", table,
            eventFilter = StreamEventFilter.fromRegex(exampleCatalog, "^main.*$".toRegex())
        ).fetchEventsAfter().toList()
        assertEquals(2, regexReceived.count())

        val listReceived = ExposedEventSource(
            db, "event-stream", table,
            eventFilter = StreamEventFilter.fromTypes(exampleCatalog, EventB::class, EventC::class),
        ).fetchEventsAfter().toList()
        assertEquals(3, listReceived.count())
    }

    val exampleCatalog = buildEventCatalog(1) {
        event<EventA>("main.a")
        event<EventB>("main.b")
        event<EventC>("other.c")
    }

    @Serializable
    class EventA(val num: Int) : Event()

    @Serializable
    class EventB(val num: Int) : Event()

    @Serializable
    class EventC(val num: Int) : Event()
}