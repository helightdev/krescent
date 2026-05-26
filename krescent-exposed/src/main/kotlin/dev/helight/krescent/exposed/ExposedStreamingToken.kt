package dev.helight.krescent.exposed

import dev.helight.krescent.source.StreamingToken
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.selectAll

sealed class ExposedStreamingToken : StreamingToken<ExposedStreamingToken> {

    override fun serialize(): String {
        // Doesn't follow the kurrentdb token format, since our interpretation of head and tail is different
        return when (this) {
            is HeadToken -> "HEAD"
            is PositionToken -> pos.toString()
        }
    }

    override fun compareTo(other: ExposedStreamingToken): Int {
        return when (this) {
            is HeadToken -> when (other) {
                is HeadToken -> 0
                is PositionToken -> -1
            }

            is PositionToken -> when (other) {
                is HeadToken -> 1
                is PositionToken -> pos.compareTo(other.pos)
            }
        }
    }

    class HeadToken : ExposedStreamingToken()
    data class PositionToken(val pos: Long) : ExposedStreamingToken()

    fun begin(table: KrescentEventLogTable): Query {
        val query = table.selectAll()
        return when (this) {
            is HeadToken -> query.orderBy(table.id)
            is PositionToken -> {
                query.orderBy(table.id).andWhere { table.id greater pos }
            }
        }
    }
}