package dev.helight.krescent

import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch

/**
 * A [StreamingEventSource] that merges multiple sources into a single stream of events.
 * The events are fully buffered and then sorted by their timestamp.
 */
class ChronoBufferedMergeStream private constructor(
    private val delegate: InMemoryEventStore,
) : StreamingEventSource<InMemoryEventStore.StreamingToken> by delegate {

    companion object {
        suspend fun create(sources: List<StreamingEventSource<*>>): ChronoBufferedMergeStream =
            channelFlow {
                for (source in sources) {
                    launch {
                        source.fetchEventsAfter().collect {
                            send(it.first)
                        }
                    }
                }
            }.toList().sortedBy { it.timestamp }.toMutableList().let {
                ChronoBufferedMergeStream(InMemoryEventStore(it))
            }
    }
}