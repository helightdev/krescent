package dev.helight.krescent.model

import dev.helight.krescent.event.EventCatalog
import dev.helight.krescent.source.EventSourcingStrategy
import dev.helight.krescent.source.StreamingEventSource
import dev.helight.krescent.source.strategy.CatchupSourcingStrategy
import dev.helight.krescent.source.strategy.NoSourcingStrategy
import dev.helight.krescent.source.strategy.StreamingSourcingStrategy

/**
 * Base class for read models that provides common extension functions.
 */
abstract class ReadModelBase(
    namespace: String,
    revision: Int,
    catalog: EventCatalog,
    configure: suspend EventModelBuilder.() -> Unit = { },
) : EventModelBase(namespace, revision, catalog, configure) {

    object Extension {
        suspend fun <M : ReadModelBase> M.strategy(
            source: StreamingEventSource,
            strategy: EventSourcingStrategy,
        ) {
            @Suppress("UNCHECKED_CAST")
            val model = build(source)
            model.strategy(strategy)
        }

        suspend fun <M : ReadModelBase> M.catchup(source: StreamingEventSource) =
            this.strategy(source, CatchupSourcingStrategy())

        suspend fun <M : ReadModelBase> M.stream(source: StreamingEventSource) =
            this.strategy(source, StreamingSourcingStrategy())

        suspend fun <M : ReadModelBase> M.restoreOnly(source: StreamingEventSource) =
            this.strategy(source, NoSourcingStrategy())

    }
}