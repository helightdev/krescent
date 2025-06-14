package dev.helight.krescent.source

import dev.helight.krescent.source.strategy.CatchupSourcingStrategy
import dev.helight.krescent.source.strategy.NoSourcingStrategy
import dev.helight.krescent.source.strategy.StreamingSourcingStrategy

interface EventSourceConsumer {
    /**
     * Streams events continuously from the event source and keeps listening for new events.
     */
    suspend fun stream() = strategy(StreamingSourcingStrategy())

    /**
     * Fetches all historic events from the event source and returns afterward.
     */
    suspend fun catchup() = strategy(CatchupSourcingStrategy())

    suspend fun twoPhaseCatchup() = strategy(CatchupSourcingStrategy())

    /**
     * Sets the event consumer to its initial state or the last stored checkpoint but doesn't start to resolve events.
     */
    suspend fun restoreOnly() = strategy(NoSourcingStrategy())

    suspend fun strategy(strategy: EventSourcingStrategy)
}