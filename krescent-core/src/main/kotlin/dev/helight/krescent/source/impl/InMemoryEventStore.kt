package dev.helight.krescent.source.impl

import dev.helight.krescent.event.EventMessage
import dev.helight.krescent.joinSequentialFlows
import dev.helight.krescent.source.EventPublisher
import dev.helight.krescent.source.ExtendedQueryableStreamingEventSource
import dev.helight.krescent.source.StreamingToken
import dev.helight.krescent.source.SubscribingEventSource
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.time.Instant

class InMemoryEventStore(
    private val events: MutableList<EventMessage> = mutableListOf(),
) : ExtendedQueryableStreamingEventSource, SubscribingEventSource, EventPublisher {

    constructor(vararg events: EventMessage) : this(events.toMutableList())

    private val mutex = Mutex()
    private val eventFlow = MutableSharedFlow<Pair<EventMessage, SequenceToken>>(
        extraBufferCapacity = 255
    )

    data class SequenceToken(val index: Int) : StreamingToken<SequenceToken> {
        override fun serialize(): String = index.toString()
        override fun compareTo(other: SequenceToken): Int = index.compareTo(other.index)
    }

    override suspend fun getHeadToken(): SequenceToken = SequenceToken(-1)

    override suspend fun getTailToken(): SequenceToken = mutex.withLock {
        SequenceToken(events.size - 1)
    }

    override suspend fun getTokenAtTime(timestamp: Instant): SequenceToken = mutex.withLock {
        val index = events.indexOfFirst { it.timestamp >= timestamp }
        if (index == -1) {
            SequenceToken(events.size - 1)
        } else {
            SequenceToken(index - 1)
        }
    }

    override suspend fun getTokenForEventId(eventId: String): SequenceToken? = mutex.withLock {
        val index = events.indexOfFirst { it.id == eventId }
        if (index == -1) {
            null
        } else {
            SequenceToken(index)
        }
    }

    override suspend fun deserializeToken(encoded: String): SequenceToken {
        return try {
            SequenceToken(encoded.toInt())
        } catch (_: NumberFormatException) {
            // Default to head if parsing fails
            getHeadToken()
        }
    }

    override suspend fun fetchEventsAfter(
        token: StreamingToken<*>?,
        limit: Int?,
    ): Flow<Pair<EventMessage, SequenceToken>> {
        return internalFetchEventsAfter(token as? SequenceToken, limit, true)
    }

    private suspend fun internalFetchEventsAfter(
        token: StreamingToken<*>?,
        limit: Int?,
        lock: Boolean,
    ): Flow<Pair<EventMessage, SequenceToken>> {
        if (token != null && token !is SequenceToken) {
            throw IllegalArgumentException("Token must be of type SequenceToken")
        }
        if (lock) mutex.lock()
        val buffer = try {
            val startIndex = (token ?: getHeadToken()).index + 1
            events.asSequence().drop(startIndex).let {
                if (limit == null) it
                else it.take(limit)
            }.mapIndexed { index, event ->
                event to SequenceToken(startIndex + index)
            }.toList()
        } finally {
            if (lock) mutex.unlock()
        }
        return flow {
            buffer.forEach {
                emit(it)
            }
        }
    }

    override suspend fun streamEvents(startToken: StreamingToken<*>?): Flow<Pair<EventMessage, SequenceToken>> {
        if (startToken != null && startToken !is SequenceToken) {
            throw IllegalArgumentException("Token must be of type SequenceToken")
        }
        mutex.lock() // This locks the mutex until both flows are being listened to
        return joinSequentialFlows(
            internalFetchEventsAfter(startToken ?: getHeadToken(), null, false), eventFlow.asSharedFlow(),
            activateCallback = {
                mutex.unlock()
            }
        )
    }


    override suspend fun subscribe(): Flow<EventMessage> {
        return eventFlow.asSharedFlow().map { it.first }
    }

    override suspend fun publish(event: EventMessage) {
        val newToken = mutex.withLock {
            events.add(event)
            SequenceToken(events.size - 1)
        }
        eventFlow.emit(event to newToken)
    }

    suspend fun serialize(): ByteArray = mutex.withLock {
        Json.encodeToString(SerializedState(events)).toByteArray(StandardCharsets.UTF_8)
    }

    suspend fun load(data: ByteArray) {
        mutex.withLock {
            events.clear()
            events.addAll(Json.decodeFromString<SerializedState>(data.toString(StandardCharsets.UTF_8)).events)
        }
    }

    suspend fun copy(): InMemoryEventStore = mutex.withLock {
        return InMemoryEventStore(events.toMutableList())
    }

    suspend fun toList(): List<EventMessage> = mutex.withLock {
        events.toList()
    }

    @Serializable
    data class SerializedState(
        val events: List<@Serializable() EventMessage>,
    )
}