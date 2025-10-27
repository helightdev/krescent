package dev.helight.krescent.source

import dev.helight.krescent.event.EventMessage
import kotlinx.coroutines.flow.Flow

internal class UpgradingStreamingEventSource(
    val original: StoredEventSource,
) : StreamingEventSource, StoredEventSource by original {
    override suspend fun streamEvents(startToken: StreamingToken<*>?): Flow<Pair<EventMessage, StreamingToken<*>>> {
        if (original is StreamingEventSource) return original.streamEvents(startToken)
        throw NotImplementedError("$original does not support streaming events")
    }
}