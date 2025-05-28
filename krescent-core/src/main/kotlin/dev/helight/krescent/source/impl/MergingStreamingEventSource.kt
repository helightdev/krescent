package dev.helight.krescent.source.impl

import dev.helight.krescent.event.EventMessage
import dev.helight.krescent.source.StreamingEventSource
import dev.helight.krescent.source.StreamingToken
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.*

class MergingStreamingEventSource(
    private val sources: Map<String, StreamingEventSource>,
    private val minAge: Long = 0L, // Minimum age of events to consider
    private val batchSize: Int = 100,
    private val pollingInterval: Long = 1000L,
) : StreamingEventSource {
    override suspend fun getHeadToken(): StreamMergingCompositeStreamingToken = StreamMergingCompositeStreamingToken(
        positions = sources.mapValues { (_, source) -> source.getHeadToken() }
    )

    override suspend fun getTailToken(): StreamMergingCompositeStreamingToken = StreamMergingCompositeStreamingToken(
        positions = sources.mapValues { (_, source) -> source.getTailToken() }
    )

    override suspend fun deserializeToken(encoded: String): StreamMergingCompositeStreamingToken {
        val map = Json.Default.decodeFromString<Map<String, String>>(encoded)
        return StreamMergingCompositeStreamingToken(
            positions = map.mapValues { (_, value) ->
                sources.values.firstOrNull()?.deserializeToken(value)
                    ?: throw IllegalArgumentException("Unknown source")
            })
    }

    inner class FetchStream(
        initialToken: StreamMergingCompositeStreamingToken,
    ) {
        val keys = sources.keys.toList()
        val buffers = keys.associateWith { LinkedList<Pair<EventMessage, StreamingToken<*>>>() }
        val cursors = initialToken.positions.toMutableMap()
        val terminated = mutableSetOf<String>()
        val deadline = Instant.now().minusMillis(minAge)!!
        val mutex = Mutex()
        var token: StreamMergingCompositeStreamingToken = initialToken

        suspend fun fetchBatch(key: String) {
            val cursor = cursors[key]
            val events = sources[key]?.fetchEventsAfter(cursor, batchSize)?.filter { (event, _) ->
                event.timestamp.isBefore(deadline) // Remove events that are too recent
            }?.toList() ?: emptyList()

            mutex.withLock {
                if (events.isNotEmpty()) {
                    buffers[key]!!.addAll(events)
                    if (events.size < batchSize) {
                        terminated.add(key) // Mark as terminated if it returned fewer events than requested
                    }
                } else {
                    terminated.add(key) // Mark as terminated if no events were returned
                }
            }
        }

        suspend fun perform(): Flow<Pair<EventMessage, StreamingToken<*>>> = coroutineScope {
            keys.map {
                async { fetchBatch(it) }
            }.awaitAll()

            channelFlow {
                while (true) {
                    // Find the earliest event across all buffers
                    val earliest = buffers.entries
                        .filter { it.value.isNotEmpty() }
                        .minByOrNull { it.value.first().first.timestamp }

                    if (earliest != null) {
                        val (key, buffer) = earliest
                        val event = buffer.poll()
                        if (event != null) {
                            val (msg, innerToken) = event
                            cursors[key] = innerToken
                            // Send the event with a new token that includes the updated cursors
                            token = StreamMergingCompositeStreamingToken(cursors.mapValues { it.value })
                            send(msg to token)
                        }

                        if (buffer.isEmpty() && !terminated.contains(key)) {
                            fetchBatch(key) // Refetch if the buffer is empty and not terminated
                        }
                    } else {
                        break // No more events to process
                    }
                }
            }
        }
    }

    override suspend fun fetchEventsAfter(
        token: StreamingToken<*>?,
        limit: Int?,
    ): Flow<Pair<EventMessage, StreamingToken<*>>> {
        if (token != null && token !is StreamMergingCompositeStreamingToken) {
            throw IllegalArgumentException("Token must be of type CompositeStreamToken")
        }
        val state = FetchStream(token ?: getHeadToken())
        return when {
            limit != null -> state.perform().take(limit)
            else -> state.perform()
        }
    }

    override suspend fun streamEvents(startToken: StreamingToken<*>?): Flow<Pair<EventMessage, StreamingToken<*>>> {
        /* TODO: Replace the polling with a buffering mechanism; adding this later though because it's quite more
             complex, requires message deduplication and tests for weird edge cases. For now, this is good enough */
        if (startToken != null && startToken !is StreamMergingCompositeStreamingToken) {
            throw IllegalArgumentException("Token must be of type CompositeStreamToken")
        }
        return channelFlow {
            var lastToken: StreamMergingCompositeStreamingToken = startToken ?: getHeadToken()
            while (true) {
                val state = FetchStream(lastToken)
                state.perform().collect {
                    send(it)
                }
                lastToken = state.token
                delay(pollingInterval)
            }
        }
    }
}

class StreamMergingCompositeStreamingToken(
    val positions: Map<String, StreamingToken<*>>,
) : StreamingToken<StreamMergingCompositeStreamingToken> {

    override fun serialize(): String = Json.encodeToString<Map<String, String>>(
        positions.mapValues { it.value.serialize() })

    @Suppress("UNCHECKED_CAST")
    override fun compareTo(other: StreamMergingCompositeStreamingToken): Int {
        if (other.positions.size != positions.size) {
            error("Multiple streaming tokens have different lengths: ${other.positions.size}")
        } else {
            if (other.positions.keys != positions.keys) {
                error("Multiple streaming tokens have different keys: ${other.positions.keys} vs ${positions.keys}")
            }
        }

        for ((key, token) in positions) {
            val otherComp: Comparable<Any> = other.positions[key] as? Comparable<Any> ?: continue
            val selfComp = token as Comparable<Any>
            val comparison = selfComp.compareTo(otherComp)
            if (comparison != 0) return comparison
        }
        return 0
    }
}