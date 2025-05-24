package dev.helight.krescent.models

import dev.helight.krescent.checkpoint.CheckpointStorage
import dev.helight.krescent.checkpoint.CheckpointStrategy
import dev.helight.krescent.checkpoint.CheckpointSupport
import dev.helight.krescent.checkpoint.CheckpointingEventSourceConsumer
import dev.helight.krescent.event.EventCatalog
import dev.helight.krescent.event.EventStreamProcessor
import dev.helight.krescent.event.VirtualEventIngest
import dev.helight.krescent.event.processor.BroadcastEventStreamProcessor
import dev.helight.krescent.event.processor.TransformingModelEventProcessor
import dev.helight.krescent.source.EventSourceConsumer
import dev.helight.krescent.source.ReplayingEventSourceConsumer
import dev.helight.krescent.source.StreamingEventSource
import kotlin.reflect.KProperty

fun StreamingEventSource<*>.buildReadModel(
    namespace: String,
    revision: Int,
    catalog: EventCatalog,
    block: ReadModelBuilder.() -> Unit,
): EventSourceConsumer {
    val builder = ReadModelBuilder(namespace, revision, catalog, this)
    builder.block()
    return builder.build()
}

class ReadModelBuilder(
    val namespace: String,
    val revision: Int,
    val catalog: EventCatalog,
    val source: StreamingEventSource<*>,
) : ExtensionAwareBuilder {
    private val virtualEvents = mutableListOf<Pair<String, VirtualEventIngest>>()
    private val checkpointing = mutableListOf<CheckpointSupport>()
    private val extensions = mutableListOf<ModelExtension<*>>()

    private var checkpointConfig: CheckpointConfiguration? = null
    private var handler: EventStreamProcessor? = null

    operator fun <T> ModelExtension<T>.getValue(thisRef: Any?, property: KProperty<*>): T {
        return this.unpack()
    }

    override fun <T : ModelExtension<*>> registerExtension(extension: T): T {
        if (!extensions.contains(extension)) {
            println("Registering extension: ${extension::class.simpleName}")
            extensions.add(extension)
            if (extension is CheckpointSupport) checkpointing.add(extension)
        }
        return extension
    }

    @Suppress("unused")
    fun virtualEventStream(
        streamId: String,
        stream: VirtualEventIngest,
    ) {
        virtualEvents.add(streamId to stream)
    }

    fun handler(
        block: EventStreamProcessor,
    ) {
        handler = block
    }

    fun withCheckpoints(
        checkpointStorage: CheckpointStorage,
        strategy: CheckpointStrategy,
    ) {
        checkpointConfig = CheckpointConfiguration(checkpointStorage, strategy)
    }

    internal fun build(): EventSourceConsumer {
        val handler = this.handler ?: throw IllegalStateException("No event handler configured, please call handler()")
        val virtualEvents = this.virtualEvents.toList()
        val broadcast = BroadcastEventStreamProcessor(
            buildList {
                add(handler)
                for (extension in extensions) {
                    add { extension.handleEvent(it) }
                }
            }
        )

        val consumer = TransformingModelEventProcessor(catalog, virtualEvents, broadcast)
        if (checkpointConfig != null) {
            val (storage, strategy) = checkpointConfig!!
            return CheckpointingEventSourceConsumer(
                namespace = namespace, revision = revision, strategy = strategy, source = source,
                checkpointStorage = storage, additionalCheckpoints = checkpointing, consumer = consumer
            )
        } else {
            return ReplayingEventSourceConsumer(
                source = source, consumer = consumer
            )
        }
    }

    private data class CheckpointConfiguration(
        val storage: CheckpointStorage,
        val strategy: CheckpointStrategy,
    )

}