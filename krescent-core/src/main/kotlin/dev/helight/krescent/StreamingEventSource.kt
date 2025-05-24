package dev.helight.krescent

import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Interface for an event source that provides a replayable stream of events.
 * Supports token-based positioning for fetching events.
 */
interface StreamingEventSource<TOKEN_TYPE: StreamingToken<TOKEN_TYPE>> {

    /**
     * Gets a token for the head of the stream, which is *before* the first event in the stream.
     * Fetching after the head token will return the first event in the stream.
     */
    suspend fun getHeadToken(): TOKEN_TYPE

    /**
     * Gets a token for the tail of the stream, which is *at the position* of the last event in the stream.
     * Fetching after the tail token will return no events if no new events are published.
     */
    suspend fun getTailToken(): TOKEN_TYPE

    /**
     * Gets a token pointing before the first event at or after the specified timestamp.
     *
     * @param timestamp The timestamp to scan for
     */
    suspend fun getTokenAtTime(timestamp: Instant): TOKEN_TYPE

    /**
     * Gets a token for a specific event ID in the stream.
     *
     * @param eventId The event's ID to get a token for
     */
    suspend fun getTokenForEventId(eventId: String): TOKEN_TYPE?

    /**
     * Deserializes the provided encoded string into a Token.
     *
     * @param encoded The encoded string representation of a token.
     * @return The deserialized Token instance.
     */
    suspend fun deserializeToken(encoded: String): TOKEN_TYPE

    /**
     * Fetches events after the specified token.
     *
     * @param token The token to fetch events after, defaults to head
     * @param limit Maximum number of events to fetch, null for no limit
     * @return A pair of the flow of events and the updated token for the next fetch
     */
    suspend fun fetchEventsAfter(token: TOKEN_TYPE? = null, limit: Int? = null): Flow<Pair<EventMessage, TOKEN_TYPE>>

    /**
     * Creates a flow of all events in the stream starting from the specified token.
     * Then it will continue to stream new events as they are published.
     *
     * @param startToken The token to start from, defaults to head
     * @return A flow of all events
     */
    suspend fun streamEvents(startToken: TOKEN_TYPE? = null): Flow<Pair<EventMessage, TOKEN_TYPE>>
}

/**
 * A token representing a position in the event stream.
 * The implementation can specify what exactly the token encapsulates.
 */
interface StreamingToken<SELF: StreamingToken<SELF>> : Comparable<SELF> {
    /**
     * Serializes the token into a string representation.
     *
     * @return A string representing the serialized token.
     */
    fun serialize(): String
}