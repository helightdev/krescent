package dev.helight.krescent.model.projection

import dev.helight.krescent.checkpoint.CheckpointBucket
import dev.helight.krescent.checkpoint.CheckpointSupport
import dev.helight.krescent.event.Event
import dev.helight.krescent.event.EventStreamProcessor
import dev.helight.krescent.event.SystemStreamHeadEvent
import dev.helight.krescent.model.ExtensionAwareBuilder
import dev.helight.krescent.model.ModelExtension
import kotlinx.serialization.json.JsonElement

class MemoryProjection<T>(
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
        println("Checkpoint created for namespace $namespace: ${bucket[namespace]}")
    }

    override suspend fun restoreCheckpoint(bucket: CheckpointBucket) {
        val data = bucket[namespace] ?: error("No checkpoint found for namespace $namespace")
        load(data, model)
        println("Checkpoint restored for namespace $namespace: $data")
    }

    companion object {
        fun <T> ExtensionAwareBuilder.memoryProjection(
            namespace: String,
            model: T,
            store: suspend (T) -> JsonElement,
            load: suspend (JsonElement, T) -> Unit,
            initialize: suspend () -> Unit = { },
        ): MemoryProjection<T> {
            return registerExtension(MemoryProjection(namespace, model, store, load, initialize))
        }
    }
}