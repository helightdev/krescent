package dev.helight.krescent.exposed

import dev.helight.krescent.source.StreamingToken
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.selectAll

sealed class ExposedStreamingToken : StreamingToken<ExposedStreamingToken> {

    override fun serialize(): String {
        // Doesn't follow the kurrentdb token format, since our interpretation of head and tail is different
        return when (this) {
            is HeadToken -> "HEAD"
            is TailToken -> "TAIL"
            is PositionToken -> pos.toString()
        }
    }

    override fun compareTo(other: ExposedStreamingToken): Int {
        return when (this) {
            is HeadToken -> when (other) {
                is HeadToken -> 0
                is TailToken -> -1
                is PositionToken -> -1
            }

            is TailToken -> when (other) {
                is HeadToken -> 1
                is TailToken -> 0
                is PositionToken -> 1
            }

            is PositionToken -> when (other) {
                is HeadToken -> 1
                is TailToken -> -1
                is PositionToken -> pos.compareTo(other.pos)
            }
        }
    }

    class HeadToken : ExposedStreamingToken()
    class TailToken : ExposedStreamingToken()
    data class PositionToken(val pos: Long) : ExposedStreamingToken()

    fun begin(table: KrescentTable): Query {
        val query = table.selectAll()
        return when (this) {
            is HeadToken -> query.orderBy(table.id)
            is TailToken -> throw IllegalArgumentException("Cannot apply TailToken to a query")
            is PositionToken -> query.orderBy(table.id).where { table.id greater pos }
        }
    }
}