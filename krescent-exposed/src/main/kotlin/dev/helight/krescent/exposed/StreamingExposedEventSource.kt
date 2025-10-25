package dev.helight.krescent.exposed

import dev.helight.krescent.event.EventMessage
import dev.helight.krescent.source.StreamingEventSource
import dev.helight.krescent.source.StreamingToken
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import kotlin.math.min
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class StreamingExposedEventSource(
    val table: KrescentTable,
    val database: Database,
    val streamId: String? = null,
    val streamIdMatcher: StreamIdMatcher = StreamIdMatcher.EQ,
    val batchSize: Int = 20,
    val pollingDelay: Long = 500L,
) : StreamingEventSource {
    override suspend fun getHeadToken(): StreamingToken<*> {
        return ExposedStreamingToken.HeadToken()
    }

    override suspend fun getTailToken(): StreamingToken<*> {
        return ExposedStreamingToken.TailToken()
    }

    override suspend fun deserializeToken(encoded: String): StreamingToken<*> {
        return when (encoded) {
            "HEAD" -> ExposedStreamingToken.HeadToken()
            "TAIL" -> ExposedStreamingToken.TailToken()
            else -> ExposedStreamingToken.PositionToken(encoded.toLong())
        }
    }

    private fun Query.withStreamIdFilter(): Query {
        return when (streamId) {
            null -> this
            else -> when (streamIdMatcher) {
                StreamIdMatcher.EQ -> this.where { table.streamId eq streamId }
                StreamIdMatcher.LIKE -> this.where { table.streamId like streamId }
                StreamIdMatcher.REGEX -> this.where { table.streamId regexp streamId }
            }
        }
    }

    private suspend fun peakEnd(): ExposedStreamingToken {
        return jdbcSuspendTransaction(database) {
            val last = table
                .select(table.id)
                .orderBy(table.id, SortOrder.DESC)
                .withStreamIdFilter()
                .limit(1)
                .firstOrNull()
            when (last) {
                null -> ExposedStreamingToken.HeadToken()
                else -> ExposedStreamingToken.PositionToken(last[table.id].value)
            }
        }
    }

    private fun mapRowToPair(row: ResultRow): Pair<EventMessage, ExposedStreamingToken> {
        val event = EventMessage(
            id = row[table.uid].toString(),
            type = row[table.type],
            timestamp = row[table.timestamp],
            payload = row[table.data],
        )
        val token = ExposedStreamingToken.PositionToken(row[table.id].value)
        return event to token
    }

    private suspend fun fetchBatch(token: ExposedStreamingToken, maxSize: Int? = null): BatchResult {
        val actualBatchSize = when (maxSize) {
            null -> batchSize
            else -> min(batchSize, maxSize)
        }
        val list = jdbcSuspendTransaction(database) {
            token.begin(table)
                .withStreamIdFilter()
                .limit(actualBatchSize)
                .map(::mapRowToPair)
                .toList()
        }
        val endToken = list.lastOrNull()?.second ?: peakEnd()
        return BatchResult(
            events = list,
            endToken = endToken,
            reachedEnd = list.size < actualBatchSize || endToken is ExposedStreamingToken.TailToken,
        )
    }

    override suspend fun fetchEventsAfter(
        token: StreamingToken<*>?,
        limit: Int?,
    ): Flow<Pair<EventMessage, StreamingToken<*>>> {
        val parsedToken = when (token) {
            null -> ExposedStreamingToken.HeadToken()
            is ExposedStreamingToken -> token
            else -> throw IllegalArgumentException("Token must be of type ExposedStreamingToken")
        }
        if (parsedToken is ExposedStreamingToken.TailToken) return emptyFlow()
        var currentToken = parsedToken
        var remaining = limit?.toLong() ?: Long.MAX_VALUE
        return channelFlow {
            while (true) {
                val batch = fetchBatch(currentToken, maxSize = limit)
                for (event in batch.events) {
                    send(event)
                    if (--remaining <= 0) return@channelFlow
                }
                if (batch.reachedEnd) break
                currentToken = batch.endToken
            }
        }
    }

    override suspend fun streamEvents(startToken: StreamingToken<*>?): Flow<Pair<EventMessage, StreamingToken<*>>> =
        coroutineScope {
            val parsedToken = when (startToken) {
                null -> ExposedStreamingToken.HeadToken()
                is ExposedStreamingToken.TailToken -> peakEnd()
                is ExposedStreamingToken -> startToken
                else -> throw IllegalArgumentException("Token must be of type ExposedStreamingToken")
            }
            var currentToken: ExposedStreamingToken = parsedToken
            return@coroutineScope flow {
                while (true) {
                    val batch = fetchBatch(currentToken)
                    for (event in batch.events) {
                        emit(event)
                    }
                    currentToken = batch.endToken
                    if (batch.reachedEnd) {
                        delay(pollingDelay)
                    }
                }
            }
        }

    private data class BatchResult(
        val events: List<Pair<EventMessage, StreamingToken<*>>>,
        val endToken: ExposedStreamingToken,
        val reachedEnd: Boolean,
    )
}