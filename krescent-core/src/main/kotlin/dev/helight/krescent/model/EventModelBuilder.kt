package dev.helight.krescent.model

import dev.helight.krescent.checkpoint.CheckpointStorage
import dev.helight.krescent.checkpoint.CheckpointStrategy
import dev.helight.krescent.checkpoint.CheckpointSupport
import dev.helight.krescent.checkpoint.CheckpointingEventSourceConsumer
import dev.helight.krescent.event.EventCatalog
import dev.helight.krescent.event.EventStreamProcessor
import dev.helight.krescent.event.VirtualEventIngest
import dev.helight.krescent.event.processor.BroadcastEventStreamProcessor
import dev.helight.krescent.event.processor.TransformingModelEventProcessor
import dev.helight.krescent.source.ReplayingEventSourceConsumer
import dev.helight.krescent.source.StreamingEventSource
import kotlin.reflect.KProperty

/**
 * Functional DSL to build an [EventModel] from a [StreamingEventSource].
 *
 * Class-based alternatives are available with [EventModelBase] and [ReducingModel] and their
 * read/write variants.
 */
fun StreamingEventSource.buildEventModel(
    namespace: String,
    revision: Int,
    catalog: EventCatalog,
    block: EventModelBuilder.() -> Unit,
): EventModel {
    val builder = EventModelBuilder(namespace, revision, catalog, this)
    builder.block()
    return builder.build()
}

class EventModelBuilder(
    val namespace: String,
    val revision: Int,
    val catalog: EventCatalog,
    val source: StreamingEventSource,
    private val extensions: MutableList<ModelExtension<*>> = mutableListOf(),
    private var handler: EventStreamProcessor? = null,
) : ExtensionAwareBuilder {

    private val virtualEvents: MutableList<Pair<String, VirtualEventIngest>> = mutableListOf()
    private var checkpointConfig: CheckpointConfiguration? = null

    operator fun <T> ModelExtension<T>.getValue(thisRef: Any?, property: KProperty<*>): T {
        return this.unpack()
    }

    /**
     * Registers an extension for the model.
     *
     * @param extension The [ModelExtension] to register.
     * @return The registered extension.
     */
    override fun <T : ModelExtension<*>> registerExtension(extension: T): T {
        if (!extensions.contains(extension)) {
            extensions.add(extension)
        }
        return extension
    }

    /**
     * Registers a virtual event stream for this model.
     *
     * @param streamId The unique identifier for the virtual event stream.
     * @param stream The [VirtualEventIngest] processor that is used to generate the virtual events.
     */
    @Suppress("unused")
    fun virtualEventStream(
        streamId: String,
        stream: VirtualEventIngest,
    ) {
        virtualEvents.add(streamId to stream)
    }

    /**
     * Sets the event stream processor that handles events for the model.
     */
    fun handler(
        block: EventStreamProcessor,
    ) {
        handler = block
    }

    /**
     * Configures checkpointing for the event model by specifying the storage and strategy to be used.
     *
     * @param checkpointStorage The storage mechanism to persist and retrieve checkpoints.
     * @param strategy The strategy defining how and when checkpoints are created during event message processing.
     */
    fun useCheckpoints(
        checkpointStorage: CheckpointStorage,
        strategy: CheckpointStrategy,
    ) {
        checkpointConfig = CheckpointConfiguration(checkpointStorage, strategy)
    }

    internal fun build(): EventModel {
        val handler = this.handler ?: throw IllegalStateException("No event handler configured, please call handler()")
        val virtualEvents = this.virtualEvents.toList()
        val checkpointing = buildList {
            if (handler is CheckpointSupport) add(handler)
            addAll(virtualEvents.map { it.second }.filterIsInstance<CheckpointSupport>())
            addAll(extensions.filterIsInstance<CheckpointSupport>())
        }

        val broadcast = BroadcastEventStreamProcessor(
            buildList {
                add(handler)
                addAll(extensions.filterIsInstance<EventStreamProcessor>())
            }
        )

        val consumer = TransformingModelEventProcessor(catalog, virtualEvents, broadcast)
        val model = when {
            checkpointConfig != null -> {
                val (storage, strategy) = checkpointConfig!!
                EventModel(
                    consumer = CheckpointingEventSourceConsumer(
                        namespace = namespace,
                        version = "E${catalog.revision}M${revision}",
                        checkpointStrategy = strategy,
                        source = source,
                        checkpointStorage = storage,
                        checkpointSupports = checkpointing,
                        consumer = consumer
                    ),
                    doorstep = consumer
                )
            }

            else -> {
                EventModel(
                    consumer = ReplayingEventSourceConsumer(
                        source = source, consumer = consumer
                    ),
                    doorstep = consumer
                )
            }
        }
        extensions.forEach {
            it.modelCreatedCallback(model)
        }
        return model
    }

    private data class CheckpointConfiguration(
        val storage: CheckpointStorage,
        val strategy: CheckpointStrategy,
    )
}