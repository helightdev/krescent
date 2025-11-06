package dev.helight.krescent.model

import dev.helight.krescent.event.EventCatalog
import dev.helight.krescent.event.SystemStreamRestoredEvent
import dev.helight.krescent.model.EventModelBase.Extension.withConfiguration
import dev.helight.krescent.source.EventSourcingStrategy
import dev.helight.krescent.source.StoredEventSource
import dev.helight.krescent.source.StreamingEventSource
import dev.helight.krescent.source.UpgradingStreamingEventSource
import dev.helight.krescent.source.impl.InMemoryEventStore

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

        suspend fun <M : ReadModelBase> M.catchup(source: StoredEventSource): M {
            val model = build(UpgradingStreamingEventSource(source))
            model.catchup()
            return this
        }

        suspend fun <M : ReadModelBase> M.stream(source: StreamingEventSource) {
            val model = build(source)
            model.stream()
        }

        suspend fun <M : ReadModelBase> M.restoreOnly(): M? {
            var hasRestored = false
            withConfiguration {
                registerProcessor {
                    if (it is SystemStreamRestoredEvent) {
                        hasRestored = true
                    }
                }
            }
            val model = build(InMemoryEventStore())
            model.restore()
            return if (hasRestored) this else null
        }

    }
}