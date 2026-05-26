package dev.helight.krescent.exposed

import dev.helight.krescent.event.EventMessage
import dev.helight.krescent.source.StoredEventSource
import dev.helight.krescent.source.StreamingToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.datetime.toDeprecatedInstant
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.regexp
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.json.contains
import kotlin.math.min
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalTime::class)
class ExposedEventSource(
    val database: Database,
    val streamId: String? = null,
    val table: KrescentEventLogTable = KrescentEventLogTable(),
    val streamIdMatcher: StreamIdMatcher = StreamIdMatcher.EQ,
    val eventFilter: StreamEventFilter? = null,
    val payloadFilter: StreamPayloadFilter? = null,
    val batchSize: Int = 500,
) : StoredEventSource {
    override suspend fun getHeadToken(): StreamingToken<*> {
        return ExposedStreamingToken.HeadToken()
    }

    override suspend fun getTailToken(): StreamingToken<*> = peakEnd()

    override suspend fun deserializeToken(encoded: String): StreamingToken<*> {
        return when (encoded) {
            "HEAD" -> ExposedStreamingToken.HeadToken()
            else -> ExposedStreamingToken.PositionToken(encoded.toLong())
        }
    }

    private fun Query.withFilterClause(): Query {
        when (streamId) {
            null -> null
            else -> when (streamIdMatcher) {
                StreamIdMatcher.EQ -> table.streamId eq streamId
                StreamIdMatcher.LIKE -> table.streamId like streamId
                StreamIdMatcher.REGEX -> table.streamId regexp streamId
            }
        }?.let { andWhere { it } }

        if (eventFilter != null) andWhere { table.type inList eventFilter.eventNames }
        if (payloadFilter != null) andWhere { table.data.contains(payloadFilter.toQuery()) }

        return this
    }

    private suspend fun peakEnd(): ExposedStreamingToken {
        return jdbcSuspendTransaction(database) {
            val last = table
                .select(table.id)
                .orderBy(table.id, SortOrder.DESC)
                .withFilterClause()
                .limit(1)
                .firstOrNull()
            when (last) {
                null -> ExposedStreamingToken.HeadToken()
                else -> ExposedStreamingToken.PositionToken(last[table.id].value)
            }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun mapRowToPair(row: ResultRow): Pair<EventMessage, ExposedStreamingToken> {
        val event = EventMessage(
            id = row[table.uid].toString(),
            type = row[table.type],
            timestamp = row[table.timestamp].toDeprecatedInstant(),
            payload = row[table.data] ,
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
                .withFilterClause()
                .limit(actualBatchSize)
                .map(::mapRowToPair)
                .toList()
        }
        val endToken = list.lastOrNull()?.second
        return if (endToken == null) {
            BatchResult(
                events = list,
                endToken = peakEnd(),
                reachedEnd = true
            )
        } else {
            BatchResult(
                events = list,
                endToken = endToken,
                reachedEnd = list.size < actualBatchSize
            )
        }
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

    private data class BatchResult(
        val events: List<Pair<EventMessage, StreamingToken<*>>>,
        val endToken: ExposedStreamingToken,
        val reachedEnd: Boolean,
    )
}