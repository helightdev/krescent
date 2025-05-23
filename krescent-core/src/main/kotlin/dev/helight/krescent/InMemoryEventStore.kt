package dev.helight.krescent

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant


class InMemoryEventStore : StreamingEventSource, SubscribingEventSource, EventPublisher {
    private val mutex = Mutex()
    private val events = mutableListOf<EventMessage>()
    private val eventFlow = MutableSharedFlow<Pair<EventMessage, StreamingEventSource.Token>>(
        extraBufferCapacity = 255
    )

    data class InMemoryToken(val index: Int) : StreamingEventSource.Token {
        override fun serialize(): String = index.toString()
    }

    override suspend fun getHeadToken(): StreamingEventSource.Token = InMemoryToken(-1)

    override suspend fun getTailToken(): StreamingEventSource.Token = mutex.withLock {
        InMemoryToken(events.size - 1)
    }

    override suspend fun getTokenAtTime(timestamp: Instant): StreamingEventSource.Token = mutex.withLock {
        val index = events.indexOfFirst { it.timestamp >= timestamp }
        if (index == -1) {
            InMemoryToken(events.size - 1)
        } else {
            InMemoryToken(index - 1)
        }
    }

    override suspend fun getTokenForEventId(eventId: String): StreamingEventSource.Token? = mutex.withLock {
        val index = events.indexOfFirst { it.id == eventId }
        if (index == -1) {
            null
        } else {
            InMemoryToken(index)
        }
    }

    override suspend fun deserializeToken(encoded: String): StreamingEventSource.Token {
        return try {
            InMemoryToken(encoded.toInt())
        } catch (e: NumberFormatException) {
            // Default to head if parsing fails
            getHeadToken()
        }
    }

    override suspend fun fetchEventsAfter(
        token: StreamingEventSource.Token,
        limit: Int?,
    ): Flow<Pair<EventMessage, StreamingEventSource.Token>> {
        val buffer = mutex.withLock {
            if (token !is InMemoryToken) error("Invalid token type")
            val startIndex = token.index + 1
            events.drop(startIndex).let {
                if (limit == null) it
                else it.take(limit)
            }.toList().mapIndexed { index, event ->
                event to InMemoryToken(startIndex + index)
            }
        }
        return flow {
            buffer.forEach {
                emit(it)
            }
        }
    }

    override suspend fun streamEvents(startToken: StreamingEventSource.Token?): Flow<Pair<EventMessage, StreamingEventSource.Token>> {
        return joinSequentialFlows(
            fetchEventsAfter(startToken ?: getHeadToken()),
            eventFlow.asSharedFlow()
        )
    }


    override suspend fun subscribe(): Flow<EventMessage> {
        return eventFlow.asSharedFlow().map { it.first }
    }

    override suspend fun publish(event: EventMessage) {
        val newToken = mutex.withLock {
            events.add(event)
            InMemoryToken(events.size - 1)
        }
        eventFlow.emit(event to newToken)
    }

}
