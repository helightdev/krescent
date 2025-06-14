package dev.helight.krescent.source

import dev.helight.krescent.event.EventMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

/**
 * Interface for an event source that provides a replayable stream of events.
 * Supports token-based positioning for fetching events.
 */
interface StreamingEventSource {

    /**
     * Gets a token for the head of the stream, which is *before* the first event in the stream.
     * Fetching after the head token will return the first event in the stream.
     */
    suspend fun getHeadToken(): StreamingToken<*>

    /**
     * Gets a token for the tail of the stream, which is always *at the position* of the last event in the stream.
     * Fetching after the tail token will return no events if no new events are published.
     *
     * The exact behavior of the tail token is implementation-dependent. You should not assume it to be an actual
     * token pointing to the last event, more like a marker indicating the end of the stream.
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
    suspend fun fetchEventsAfter(token: StreamingToken<*>? = null, limit: Int? = null): Flow<Pair<EventMessage, StreamingToken<*>>>

    /**
     * Creates a flow of all events in the stream starting from the specified token.
     * Then it will continue to stream new events as they are published.
     *
     * @param startToken The token to start from, defaults to head
     * @return A flow of all events
     */
    suspend fun streamEvents(startToken: StreamingToken<*>? = null): Flow<Pair<EventMessage, StreamingToken<*>>>
}

interface ExtendedQueryableStreamingEventSource : StreamingEventSource {
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