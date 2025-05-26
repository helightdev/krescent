package dev.helight.krescent.model

import dev.helight.krescent.event.EventCatalog
import dev.helight.krescent.event.EventStreamProcessor
import dev.helight.krescent.source.StreamingEventSource
import kotlin.reflect.KProperty

abstract class EventModelBase(
    val namespace: String,
    val revision: Int,
    val catalog: EventCatalog,
    val configure: suspend EventModelBuilder.() -> Unit = { },
) : ExtensionAwareBuilder, EventStreamProcessor {

    internal val extensions = mutableListOf<ModelExtension<*>>()
    private var hasBeenBuilt = false

    override fun <E : ModelExtension<*>> registerExtension(extension: E): E {
        if (!extensions.contains(extension)) {
            extensions.add(extension)
        }
        return extension
    }

    operator fun <E> ModelExtension<E>.getValue(thisRef: Any?, property: KProperty<*>): E {
        return this.unpack()
    }

    open suspend fun EventModelBuilder.configure() {}

    suspend fun build(source: StreamingEventSource): EventModel {
        if (hasBeenBuilt) error("This model instance has already been built once, please create a new instance.")
        hasBeenBuilt = true
        val builder = EventModelBuilder(
            namespace, revision, catalog, source = source,
            handler = this,
            extensions = extensions.toMutableList()
        )
        configure(builder)
        builder.configure()
        return builder.build()
    }
}