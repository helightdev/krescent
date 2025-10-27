@file:Suppress("ExposedReference")

package dev.helight.krescent.exposed

import dev.helight.krescent.checkpoint.AlwaysCheckpointStrategy
import dev.helight.krescent.checkpoint.CheckpointValidationException
import dev.helight.krescent.event.Event
import dev.helight.krescent.event.buildEventCatalog
import dev.helight.krescent.exposed.ExposedTableProjector.Companion.exposedTableProjection
import dev.helight.krescent.model.EventModelBase.Extension.withConfiguration
import dev.helight.krescent.model.ReadModelBase
import dev.helight.krescent.model.ReadModelBase.Extension.catchup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.assertThrows
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

@Testcontainers
class ExposedTableProjectorTest {

    companion object {

        @Container
        private val postgres = GenericContainer("postgres:latest")
            .withExposedPorts(5432)
            .withEnv("POSTGRES_USER", "root")
            .withEnv("POSTGRES_PASSWORD", "example")
            .withStartupTimeout(Duration.ofSeconds(60))

        private val connectionString get() = "jdbc:postgresql://${postgres.host}:${postgres.getMappedPort(5432)}/root"
        private val mutex = Mutex()

        fun connect(): Database = Database.connect(
            url = connectionString,
            driver = "org.postgresql.Driver",
            user = "root",
            password = "example"
        )
    }

    fun execWithTable(block: suspend CoroutineScope.(Database, KrescentEventsTable) -> Unit) = runBlocking {
        mutex.withLock {
            val db = connect()
            val tableName = "krescent_${UUID.randomUUID()}"
            val table = KrescentEventsTable(tableName)
            table.create(db)
            try {
                this.block(db, table)
                delay(300)
            } finally {
                runCatching { table.drop(db) }
                dropProjection()
            }
        }
    }

    @Test
    fun `Create projection from event existing events`() = execWithTable { database, table ->
        val source = ExposedEventSource(database, table = table)
        val publisher = ExposedEventPublisher(database, "stream", table = table)
        publisher.publishAll(eventList)
        BookStoreReadModel(database).catchup(source)
        validateProjection()
    }

    @Test
    fun `Checkpoint integration and validation`() = execWithTable { database, table ->
        val checkpoints = ExposedCheckpointStorage(database)
        checkpoints.ensureCreated()

        val source = ExposedEventSource(database, table = table)
        val publisher = ExposedEventPublisher(database, "stream", table = table)

        // The first 3 events get published
        publisher.publishAll(eventList.take(3))
        BookStoreReadModel(database, "resuming").withConfiguration {
            useCheckpoints(checkpoints, AlwaysCheckpointStrategy)
        }.catchup(source)
        transaction {
            assertEquals(
                2, ProjectionTable.selectAll()
                .where { ProjectionTable.name eq "alpha" }
                .firstOrNull()?.get(ProjectionTable.count))
        }
        println()

        // We gracefully shut down and try resuming now with the rest
        publisher.publishAll(eventList.drop(3))
        BookStoreReadModel(database, "resuming").withConfiguration {
            useCheckpoints(checkpoints, AlwaysCheckpointStrategy)
        }.catchup(source)
        validateProjection()

        // Simulate what happens when the projection gets dropped but not the checkpoint
        dropProjection()
        assertThrows<CheckpointValidationException> {
            BookStoreReadModel(database, "resuming").withConfiguration {
                rebuildOnInvalidCheckpoint = false
                useCheckpoints(checkpoints, AlwaysCheckpointStrategy)
            }.catchup(source)
        }

        // This should rebuild without issues even with an invalid checkpoint
        val model = BookStoreReadModel(database, "resuming")
        model.withConfiguration {
            rebuildOnInvalidCheckpoint = true
            useCheckpoints(checkpoints, AlwaysCheckpointStrategy)
        }.catchup(source)
        validateProjection()

        // Check revision number validation
        model.projector.writeTransaction { }
        assertThrows<CheckpointValidationException> {
            BookStoreReadModel(database, "resuming").withConfiguration {
                rebuildOnInvalidCheckpoint = false
                useCheckpoints(checkpoints, AlwaysCheckpointStrategy)
            }.catchup(source)
        }
    }

    val eventList = listOf(
        bookEventCatalog.create(BookAddedEvent("alpha")),
        bookEventCatalog.create(BookAddedEvent("beta")),
        bookEventCatalog.create(BookAddedEvent("alpha")),
        bookEventCatalog.create(BookRemovedEvent("alpha")),
        bookEventCatalog.create(BookRemovedEvent("beta")),
        bookEventCatalog.create(BookAddedEvent("gamma")),
        bookEventCatalog.create(BookAddedEvent("gamma")),
    )
    // State in end: Alpha 1, Beta 0, Gamma 2

    private fun validateProjection() {
        transaction {
            assertEquals(
                1, ProjectionTable.selectAll()
                    .where { ProjectionTable.name eq "alpha" }
                    .firstOrNull()?.get(ProjectionTable.count))

            assertEquals(
                2, ProjectionTable.selectAll()
                    .where { ProjectionTable.name eq "gamma" }
                    .firstOrNull()?.get(ProjectionTable.count))

            assertEquals(0, ProjectionTable.selectAll().where {
                ProjectionTable.name eq "beta"
            }.count())
        }
    }

    private fun dropProjection() = runCatching {
        transaction {
            SchemaUtils.drop(ProjectionTable)
        }
    }

    class BookStoreReadModel(
        database: Database,
        namespace: String = "book-store",
    ) : ReadModelBase(namespace, 1, bookEventCatalog) {

        val projector by exposedTableProjection(database, ProjectionTable)

        private fun getCount(name: String): Int = ProjectionTable
            .select(ProjectionTable.count)
            .where { ProjectionTable.name eq name }
            .firstOrNull()?.get(ProjectionTable.count) ?: 0

        override suspend fun process(event: Event): Unit = when (event) {
            is BookAddedEvent -> projector.writeTransaction {
                val current = getCount(event.name)
                ProjectionTable.upsert(keys = arrayOf(ProjectionTable.name)) {
                    it[name] = event.name
                    it[count] = current + 1
                }
            }

            is BookRemovedEvent -> projector.writeTransaction {
                val current = getCount(event.name)
                if (current > 1) {
                    ProjectionTable.update(where = { ProjectionTable.name eq event.name }) {
                        it[name] = event.name
                        it[count] = current - 1
                    }
                } else if (current == 1) {
                    ProjectionTable.deleteWhere { ProjectionTable.name eq event.name }
                }
            }

            else -> {}
        }
    }
}

private object ProjectionTable : Table("projection") {
    val name = text("name").uniqueIndex()
    val count = integer("count")

    override val primaryKey: PrimaryKey = PrimaryKey(name)
}

private val bookEventCatalog = buildEventCatalog(1) {
    event<BookAddedEvent>("book.added")
    event<BookRemovedEvent>("book.removed")
}

@Serializable
private data class BookAddedEvent(val name: String) : Event()

@Serializable
private data class BookRemovedEvent(val name: String) : Event()