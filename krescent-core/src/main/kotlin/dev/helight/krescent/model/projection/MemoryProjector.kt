package dev.helight.krescent.model.projection

import dev.helight.krescent.checkpoint.CheckpointBucket
import dev.helight.krescent.checkpoint.CheckpointSupport
import dev.helight.krescent.event.Event
import dev.helight.krescent.event.EventStreamProcessor
import dev.helight.krescent.event.SystemStreamHeadEvent
import dev.helight.krescent.model.ExtensionAwareBuilder
import dev.helight.krescent.model.ModelExtension
import kotlinx.serialization.json.JsonElement

/**
 * A memory projector that uses the [model] as the backing state storage and provides checkpointing support using
 * the provided [store] and [load] functions, which serialize to simple [JsonElement]. Head initialize may also
 * be done using the [initialize] function.
 *
 * @property namespace The namespace for the projector, used for checkpointing.
 * @property model The model instance that this projector will manage.
 * @property store A suspend function that serializes the model to a [JsonElement] for checkpointing.
 * @property load A suspend function that loads the model from a [JsonElement] during checkpoint restoration and applies
 * it to the given [model] instance.
 * @property initialize A suspend function that initializes the projector, called on a [SystemStreamHeadEvent].
 */
class MemoryProjector<T>(
    private val namespace: String,
    private val model: T,
    private val store: suspend (T) -> JsonElement,
    private val load: suspend (JsonElement, T) -> Unit,
    private val initialize: suspend () -> Unit = { },
) : ModelExtension<T>, EventStreamProcessor, CheckpointSupport {

    override suspend fun process(event: Event) {
        if (event is SystemStreamHeadEvent) {
            initialize()
        }
    }

    override fun unpack(): T = model

    override suspend fun createCheckpoint(bucket: CheckpointBucket) {
        bucket[namespace] = store(model)
    }

    override suspend fun restoreCheckpoint(bucket: CheckpointBucket) {
        val data = bucket[namespace] ?: error("No checkpoint found for namespace $namespace")
        load(data, model)
    }

    companion object {

        /**
         * Creates a memory projector that uses the provided [model] as the backing state storage and provides
         * checkpointing support using the provided [store] and [load] functions, which serialize to simple [JsonElement].
         * Head initialize may also be done using the [initialize] function.
         *
         * @see MemoryProjector
         */
        fun <T> ExtensionAwareBuilder.memoryProjection(
            namespace: String,
            model: T,
            store: suspend (T) -> JsonElement,
            load: suspend (JsonElement, T) -> Unit,
            initialize: suspend () -> Unit = { },
        ): MemoryProjector<T> {
            return registerExtension(MemoryProjector(namespace, model, store, load, initialize))
        }
    }
}