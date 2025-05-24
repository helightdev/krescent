package dev.helight.krescent.source

interface EventSourceConsumer {
    /**
     * Streams events continuously from the event source and keeps listening for new events.
     */
    suspend fun stream()

    /**
     * Fetches all historic events from the event source and returns afterward.
     */
    suspend fun catchup()

    /**
     * Sets the event consumer to its initial state or the last stored checkpoint but doesn't start to resolve events.
     */
    suspend fun restore()
}