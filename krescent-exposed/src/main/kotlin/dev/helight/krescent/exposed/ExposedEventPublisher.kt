package dev.helight.krescent.exposed

import dev.helight.krescent.event.EventMessage
import dev.helight.krescent.source.EventPublisher
import kotlinx.datetime.toStdlibInstant
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import java.util.*
import kotlin.time.ExperimentalTime

class ExposedEventPublisher(
    val table: KrescentTable,
    val database: Database,
    val streamId: String,
) : EventPublisher {
    @OptIn(ExperimentalTime::class)
    override suspend fun publish(event: EventMessage) {
        jdbcSuspendTransaction(database) {
            table.insert {
                it[uid] = UUID.fromString(event.id)
                it[streamId] = this@ExposedEventPublisher.streamId
                it[type] = event.type
                it[timestamp] = event.timestamp.toStdlibInstant()
                it[data] = event.payload
            }
        }
    }
}