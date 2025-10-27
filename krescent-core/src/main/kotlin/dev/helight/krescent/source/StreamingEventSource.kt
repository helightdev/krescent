package dev.helight.krescent.source

import dev.helight.krescent.event.EventMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

/**
 * Interface for an event source that provides access to stored events.
 * Supports token-based positioning for fetching events.
 */
interface StoredEventSource {
    /**
     * Gets a token for the head of the stream, which is *before* the first event in the stream.
     * Fetching after the head token will return the first event in the stream.
     */
    suspend fun getHeadToken(): StreamingToken<*>

    /**
     * Gets a token for the tail of the stream, which is always *at the position* of the last event in the stream.
     * Fetching after the tail token will return no events if no new events are published.
     *
     * The exact behavior of the tail token is implementation-dependent. If the event source also implements
     * `StreamingEventSource`, the token may only be symbolic and may always point at the tail of the stream.
     * If the event source doesn't implement streaming only providing access to stored values, one can expect the tail
     * token to point to the physical last event so that polling can properly be achieved.
     */
    suspend fun getTailToken(): StreamingToken<*>


    /**
     * Deserializes the provided encoded string into a Token.
     *
     * @param encoded The encoded string representation of a token.
     * @return The deserialized Token instance.
     */
    suspend fun deserializeToken(encoded: String): StreamingToken<*>

    /**
     * Fetches events after the specified token.
     *
     * @param token The token to fetch events after, defaults to head
     * @param limit Maximum number of events to fetch, null for no limit
     * @return A pair of the flow of events and the updated token for the next fetch
     */
    suspend fun fetchEventsAfter(
        token: StreamingToken<*>? = null,
        limit: Int? = null,
    ): Flow<Pair<EventMessage, StreamingToken<*>>>

}

/**
 * Interface for an event source that provides a replayable stream of events.
 * Supports token-based positioning for fetching events.
 */
interface StreamingEventSource : StoredEventSource {
    /**
     * Creates a flow of all events in the stream starting from the specified token.
     * Then it will continue to stream new events as they are published.
     *
     * @param startToken The token to start from, defaults to head
     * @return A flow of all events
     */
    suspend fun streamEvents(startToken: StreamingToken<*>? = null): Flow<Pair<EventMessage, StreamingToken<*>>>
}

interface ExtendedQueryableStoredEventSource : StoredEventSource {
    /**
     * Gets a token pointing before the first event at or after the specified timestamp.
     *
     * @param timestamp The timestamp to scan for
     */
    suspend fun getTokenAtTime(timestamp: Instant): StreamingToken<*>

    /**
     * Gets a token for a specific event ID in the stream.
     *
     * @param eventId The event's ID to get a token for
     */
    suspend fun getTokenForEventId(eventId: String): StreamingToken<*>?

}

