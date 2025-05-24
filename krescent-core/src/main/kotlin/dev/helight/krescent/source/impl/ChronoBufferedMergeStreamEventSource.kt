package dev.helight.krescent.source.impl

import dev.helight.krescent.source.StreamingEventSource
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch

/**
 * A [dev.helight.krescent.source.StreamingEventSource] that merges multiple sources into a single stream of events.
 * The events are fully buffered and then sorted by their timestamp.
 */
class ChronoBufferedMergeStreamEventSource private constructor(
    private val delegate: InMemoryEventStore,
) : StreamingEventSource<InMemoryEventStore.StreamingToken> by delegate {

    companion object {
        suspend fun create(sources: List<StreamingEventSource<*>>): ChronoBufferedMergeStreamEventSource =
            channelFlow {
                for (source in sources) {
                    launch {
                        source.fetchEventsAfter().collect {
                            send(it.first)
                        }
                    }
                }
            }.toList().sortedBy { it.timestamp }.toMutableList().let {
                ChronoBufferedMergeStreamEventSource(InMemoryEventStore(it))
            }
    }
}