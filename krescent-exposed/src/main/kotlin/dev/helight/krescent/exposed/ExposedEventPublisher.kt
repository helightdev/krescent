package dev.helight.krescent.exposed

import dev.helight.krescent.event.EventMessage
import dev.helight.krescent.source.EventPublisher
import kotlinx.datetime.toStdlibInstant
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.insert
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class ExposedEventPublisher(
    val database: Database,
    val streamId: String,
    val table: KrescentEventLogTable = KrescentEventLogTable(),
) : EventPublisher {
    @OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
    override suspend fun publish(event: EventMessage) {
        jdbcSuspendTransaction(database) {
            table.insert {
                it[uid] = Uuid.parse(event.id)
                it[streamId] = this@ExposedEventPublisher.streamId
                it[type] = event.type
                it[timestamp] = event.timestamp.toStdlibInstant()
                it[data] = event.payload
            }
        }
    }

    @OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
    override suspend fun publishAll(events: List<EventMessage>) {
        val ktable = table
        jdbcSuspendTransaction(database) {
            table.batchInsert(events) {
                this[ktable.uid] = Uuid.parse(it.id)
                this[ktable.streamId] = this@ExposedEventPublisher.streamId
                this[ktable.type] = it.type
                this[ktable.timestamp] = it.timestamp.toStdlibInstant()
                this[ktable.data] = it.payload
            }
        }
    }
}