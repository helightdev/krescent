package dev.helight.krescent.event

interface EventSourceConsumer {
    /**
     * Streams events continuously from the event source and keeps listening for new events.
     */
    suspend fun stream()

    /**
     * Fetches all historic events from the event source and returns afterward.
     */
    suspend fun catchup()
}