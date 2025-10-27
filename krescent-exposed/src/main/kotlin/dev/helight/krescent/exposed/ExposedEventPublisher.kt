package dev.helight.krescent.exposed

import dev.helight.krescent.event.EventMessage
import dev.helight.krescent.source.EventPublisher
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import java.util.*
import kotlin.time.ExperimentalTime

class ExposedEventPublisher(
    val database: Database,
    val streamId: String,
    val table: KrescentEventsTable = KrescentEventsTable(),
) : EventPublisher {
    @OptIn(ExperimentalTime::class)
    override suspend fun publish(event: EventMessage) {
        jdbcSuspendTransaction(database) {
            table.insert {
                it[uid] = UUID.fromString(event.id)
                it[streamId] = this@ExposedEventPublisher.streamId
                it[type] = event.type
                it[timestamp] = event.timestamp
                it[data] = event.payload
            }
        }
    }
}