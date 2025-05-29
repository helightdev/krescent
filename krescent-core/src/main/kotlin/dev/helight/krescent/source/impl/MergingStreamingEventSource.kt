package dev.helight.krescent.source.impl

import dev.helight.krescent.event.EventMessage
import dev.helight.krescent.source.StreamingEventSource
import dev.helight.krescent.source.StreamingToken
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.*
import kotlin.time.Duration.Companion.milliseconds


/**
 * A composite streaming event source that merges and manages multiple [StreamingEventSource] instances
 * into a single continuous flow of events. Events are streamed in order of their timestamps across
 * the provided sources.
 *
 * Fetch operations are performed in lazy batches, fetching a maximum of [batchSize] events from each source
 * at a time. Streaming operations use a k-way merge with a minimum age threshold for newly received events.
 *
 * @param sources A map of string keys to their corresponding [StreamingEventSource] instances. Each source
 * represents an independent stream of events to be merged.
 * @param minAge The minimum age (in milliseconds) of events to be considered safe for streaming. Events
 * newer than the specified age are delayed to ensure consistency across sources.
 * @param batchSize The maximum number of events to fetch from each source in a single batch during a fetch operation.
 * @param pollingInterval The internal polling interval (in milliseconds) for checking the incoming event buffers.
 * @param streamPrefetch A flag indicating whether to prefetch events in batches using [fetchEventsAfter]
 * before streaming live events. This generally improves memory usage since events will not build up in the buffer when
 * streams aren't overlapping, making it generally safer to use, albeit with considerably higher network calls.
 */
class MergingStreamingEventSource(
    private val sources: Map<String, StreamingEventSource>,
    private val minAge: Long = 3000L, // Minimum age of events to consider
    private val batchSize: Int = 100,
    private val pollingInterval: Long = 50L,
    private val streamPrefetch: Boolean = true,
) : StreamingEventSource {
    override suspend fun getHeadToken(): CompositeStreamingToken = CompositeStreamingToken(
        positions = sources.mapValues { (_, source) -> source.getHeadToken() })

    override suspend fun getTailToken(): CompositeStreamingToken = CompositeStreamingToken(
        positions = sources.mapValues { (_, source) -> source.getTailToken() })

    override suspend fun deserializeToken(encoded: String): CompositeStreamingToken {
        val map = Json.Default.decodeFromString<Map<String, String>>(encoded)
        return CompositeStreamingToken(
            positions = map.mapValues { (_, value) ->
                sources.values.firstOrNull()?.deserializeToken(value)
                    ?: throw IllegalArgumentException("Unknown source")
            })
    }

    override suspend fun fetchEventsAfter(
        token: StreamingToken<*>?,
        limit: Int?,
    ): Flow<Pair<EventMessage, StreamingToken<*>>> {
        if (token != null && token !is CompositeStreamingToken) {
            throw IllegalArgumentException("Token must be of type CompositeStreamToken")
        }
        val state = FetchStream(token ?: getHeadToken())
        return when {
            limit != null -> state.perform().take(limit)
            else -> state.perform()
        }
    }

    override suspend fun streamEvents(startToken: StreamingToken<*>?): Flow<Pair<EventMessage, StreamingToken<*>>> {
        if (startToken != null && startToken !is CompositeStreamingToken) {
            throw IllegalArgumentException("Token must be of type CompositeStreamToken")
        }
        val state = LiveStreaming(startToken ?: getHeadToken())
        return state.perform()
    }

    internal inner class FetchStream(
        initialToken: CompositeStreamingToken,
    ) {
        val keys = sources.keys.toList()
        val buffers = keys.associateWith { LinkedList<Pair<EventMessage, StreamingToken<*>>>() }
        val cursors = initialToken.positions.toMutableMap()
        val terminated = mutableSetOf<String>()
        val deadline = Clock.System.now().minus(minAge.milliseconds)
        val mutex = Mutex()
        var token: CompositeStreamingToken = initialToken

        suspend fun fetchBatch(key: String) {
            val cursor = cursors[key]
            val events = sources[key]?.fetchEventsAfter(cursor, batchSize)?.filter { (event, _) ->
                event.timestamp < deadline // Remove events that are too recent
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
                    val earliest = buffers.entries.filter { it.value.isNotEmpty() }
                        .minByOrNull { it.value.first().first.timestamp }

                    if (earliest != null) {
                        val (key, buffer) = earliest
                        val event = buffer.poll()
                        if (event != null) {
                            val (msg, innerToken) = event
                            cursors[key] = innerToken
                            // Send the event with a new token that includes the updated cursors
                            token = CompositeStreamingToken(cursors.mapValues { it.value })
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

    internal inner class LiveStreaming(
        initialToken: CompositeStreamingToken,
    ) {
        val cursors = initialToken.positions.toMutableMap()
        val mutex = Mutex()
        var token: CompositeStreamingToken = initialToken
        var incomingBuffer = PriorityQueue<IncomingMessage>()
        var lastAcknowledged: Instant = Instant.fromEpochSeconds(0)

        suspend fun perform(): Flow<Pair<EventMessage, StreamingToken<*>>> = coroutineScope {
            channelFlow {
                // Prefetch the "semi-live" state using the more efficient fetch method
                if (streamPrefetch) {
                    FetchStream(token).perform().collect {
                        send(it)
                        token = it.second as CompositeStreamingToken
                        lastAcknowledged = it.first.timestamp
                    }
                }

                // Update the cursor buffer with the token positions
                cursors.putAll(token.positions)

                // Start collection events from where the sources are currently at
                for ((key, source) in sources) launch {
                    source.streamEvents(cursors[key]).collect {
                        mutex.withLock {
                            incomingBuffer.add(
                                IncomingMessage(
                                    message = it.first, position = it.second, source = key
                                )
                            )
                        }
                    }
                }

                val outgoing = ArrayDeque<Pair<EventMessage, CompositeStreamingToken>>()
                while (true) {
                    // Poll old enough messages, tick internal cursors and then add them to the outgoing queue
                    val deadline = Clock.System.now() - minAge.milliseconds
                    mutex.withLock {
                        while (incomingBuffer.peek()?.message?.timestamp?.let { it < deadline } ?: false) {
                            val oldest = incomingBuffer.poll()
                            val timestamp = oldest.message.timestamp
                            if (timestamp < lastAcknowledged) {
                                throw OutOfOrderStreamException(
                                    sourceId = oldest.source, sourceToken = oldest.position, timestamp = timestamp
                                )
                            }
                            cursors[oldest.source] = oldest.position
                            token = CompositeStreamingToken(cursors.mapValues { it.value })
                            lastAcknowledged = timestamp
                            outgoing.add(oldest.message to token)
                        }
                    }

                    // Send all outgoing messages and then suspend for the polling interval
                    while (outgoing.isNotEmpty()) {
                        send(outgoing.poll())
                    }
                    delay(pollingInterval)
                }
            }
        }

    }

    internal data class IncomingMessage(
        val message: EventMessage,
        val position: StreamingToken<*>,
        val source: String,
    ) : Comparable<IncomingMessage> {
        override fun compareTo(other: IncomingMessage): Int {
            return this.message.timestamp.compareTo(other.message.timestamp)
        }
    }

    /**
     * A composite streaming token that represents the positions of multiple streaming tokens across different sources.
     * It allows for comparison and serialization of the combined state of multiple event streams.
     */
    class CompositeStreamingToken(
        val positions: Map<String, StreamingToken<*>>,
    ) : StreamingToken<CompositeStreamingToken> {

        override fun serialize(): String = Json.encodeToString<Map<String, String>>(
            positions.mapValues { it.value.serialize() })

        @Suppress("UNCHECKED_CAST")
        override fun compareTo(other: CompositeStreamingToken): Int {
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

    /**
     * Indicates that a stream has encountered a message after already having processed a message with a later timestamp.
     */
    class OutOfOrderStreamException(
        val sourceId: String,
        val sourceToken: StreamingToken<*>,
        val timestamp: Instant,
    ) : Exception(
        "Out of order message for source '$sourceId' at '${timestamp}': $sourceToken."
    )
}