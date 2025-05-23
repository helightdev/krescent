package dev.helight.krescent

import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Interface for an event source that provides a replayable stream of events.
 * Supports token-based positioning for fetching events.
 */
interface StreamingEventSource {
    /**
     * A token representing a position in the event stream.
     * The implementation can specify what exactly the token encapsulates.
     */
    interface Token {
        /**
         * Serializes the token into a string representation.
         *
         * @return A string representing the serialized token.
         */
        fun serialize(): String
    }

    /**
     * Gets a token for the head of the stream, which is *before* the first event in the stream.
     * Fetching after the head token will return the first event in the stream.
     */
    suspend fun getHeadToken(): Token

    /**
     * Gets a token for the tail of the stream, which is *at the position* of the last event in the stream.
     * Fetching after the tail token will return no events if no new events are published.
     */
    suspend fun getTailToken(): Token

    /**
     * Gets a token pointing before the first event at or after the specified timestamp.
     *
     * @param timestamp The timestamp to scan for
     */
    suspend fun getTokenAtTime(timestamp: Instant): Token

    /**
     * Gets a token for a specific event ID in the stream.
     *
     * @param eventId The event's ID to get a token for
     */
    suspend fun getTokenForEventId(eventId: String): Token?

    /**
     * Deserializes the provided encoded string into a Token.
     *
     * @param encoded The encoded string representation of a token.
     * @return The deserialized Token instance.
     */
    suspend fun deserializeToken(encoded: String): Token

    /**
     * Fetches events after the specified token.
     *
     * @param token The token to fetch events after
     * @param limit Maximum number of events to fetch, null for no limit
     * @return A pair of the flow of events and the updated token for the next fetch
     */
    suspend fun fetchEventsAfter(token: Token, limit: Int? = null): Flow<Pair<EventMessage, Token>>

    /**
     * Creates a flow of all events in the stream starting from the specified token.
     * Then it will continue to stream new events as they are published.
     *
     * @param startToken The token to start from, defaults to head
     * @return A flow of all events
     */
    suspend fun streamEvents(startToken: Token? = null): Flow<Pair<EventMessage, Token>>
}