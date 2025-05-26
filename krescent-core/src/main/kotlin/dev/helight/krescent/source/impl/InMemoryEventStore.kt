package dev.helight.krescent.source.impl

import dev.helight.krescent.event.EventMessage
import dev.helight.krescent.joinSequentialFlows
import dev.helight.krescent.source.EventPublisher
import dev.helight.krescent.source.StreamingEventSource
import dev.helight.krescent.source.StreamingToken
import dev.helight.krescent.source.SubscribingEventSource
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant

class InMemoryEventStore(
    private val events: MutableList<EventMessage> = mutableListOf(),
) : StreamingEventSource, SubscribingEventSource, EventPublisher {

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
        if (token != null && token !is SequenceToken) {
            throw IllegalArgumentException("Token must be of type SequenceToken")
        }
        val buffer = mutex.withLock {
            val startIndex = (token ?: getHeadToken()).index + 1
            events.drop(startIndex).let {
                if (limit == null) it
                else it.take(limit)
            }.toList().mapIndexed { index, event ->
                event to SequenceToken(startIndex + index)
            }
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
        return joinSequentialFlows(
            fetchEventsAfter(startToken ?: getHeadToken()), eventFlow.asSharedFlow()
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

    @OptIn(ExperimentalSerializationApi::class)
    @Suppress("unused")
    suspend fun serialize(): ByteArray = mutex.withLock {
        ByteArrayOutputStream().use {
            Json.Default.encodeToStream(SerializedState(events), it)
            it.toByteArray()
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun load(data: ByteArray) {
        mutex.withLock {
            events.clear()
            events.addAll(ByteArrayInputStream(data).use {
                Json.Default.decodeFromStream<SerializedState>(it)
            }.events)
        }
    }

    @Serializable
    data class SerializedState(
        val events: List<EventMessage>,
    )
}