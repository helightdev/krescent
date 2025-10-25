package dev.helight.krescent.source.impl

import dev.helight.krescent.event.EventMessage
import dev.helight.krescent.source.StreamingEventSource
import dev.helight.krescent.source.StreamingToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow

/**
 * A simulated implementation of the [StreamingEventSource] interface that introduces artificial delays and simulates
 * event batch sizes while delegating the actual event streaming behavior.
 *
 * @property latency The amount of delay, in milliseconds, to simulate network or processing latency.
 * This delay is applied before various operations within the source.
 * @property simulatedBatchSize The number of events to process in a single batch before introducing an additional
 * delay equal to the specified latency.
 * @property delegate The actual `StreamingEventSource` implementation to delegate the core functionality to.
 */
class SimulatedDelayStreamingEventSource(
    val latency: Long,
    val simulatedBatchSize: Int,
    val delegate: StreamingEventSource,
) : StreamingEventSource {
    override suspend fun getHeadToken(): StreamingToken<*> {
        delay(latency)
        return delegate.getHeadToken()
    }

    override suspend fun getTailToken(): StreamingToken<*> {
        delay(latency)
        return delegate.getTailToken()
    }

    override suspend fun deserializeToken(encoded: String): StreamingToken<*> = delegate.deserializeToken(encoded)

    override suspend fun fetchEventsAfter(
        token: StreamingToken<*>?,
        limit: Int?,
    ): Flow<Pair<EventMessage, StreamingToken<*>>> {
        return channelFlow {
            delay(latency)
            var currentBatchSize = 0
            delegate.fetchEventsAfter(token, limit).collect { event ->
                if (currentBatchSize >= simulatedBatchSize) {
                    delay(latency)
                    currentBatchSize = 0
                }
                ++currentBatchSize
                send(event)
            }
        }
    }

    override suspend fun streamEvents(startToken: StreamingToken<*>?): Flow<Pair<EventMessage, StreamingToken<*>>> {
        return channelFlow {
            delay(latency)
            delegate.streamEvents(startToken).collect { event ->
                delay(latency)
                send(event)
            }
        }
    }

}