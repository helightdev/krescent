package dev.helight.krescent.model.projection

import dev.helight.krescent.checkpoint.CheckpointBucket
import dev.helight.krescent.checkpoint.CheckpointSupport
import dev.helight.krescent.event.Event
import dev.helight.krescent.event.EventStreamProcessor
import dev.helight.krescent.event.SystemStreamHeadEvent
import dev.helight.krescent.model.ExtensionAwareBuilder
import dev.helight.krescent.model.ModelExtension
import kotlinx.serialization.*
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

typealias MapMap = MutableMap<String, MutableMap<String, String>>

fun main() {
    val map: MapMap = mutableMapOf(
        "key1" to mutableMapOf("subkey1" to "value1", "subkey2" to "value2"),
        "key2" to mutableMapOf("subkey3" to "value3")
    )
    val reloadable = MemoryMapBuffer(map)
    val s = Json.encodeToString(reloadable)
    println(s)
}

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

@OptIn(ExperimentalSerializationApi::class)
class StateMemoryProjection<T : ReloadableState<T>>(
    val key: String,
    val serializer: KSerializer<T>,
    val state: T,
) : ModelExtension<T>, EventStreamProcessor, CheckpointSupport {

    override suspend fun process(event: Event) {
        if (event is SystemStreamHeadEvent) {
            state.reset()
        }
    }

    override fun unpack(): T = this.state

    override suspend fun createCheckpoint(bucket: CheckpointBucket) {
        bucket.put(key, Cbor.encodeToByteArray(serializer, state))
    }

    override suspend fun restoreCheckpoint(bucket: CheckpointBucket) {
        val array = bucket.getByteArray(key) ?: error("No checkpoint found for key $key")
        state.reload(Cbor.decodeFromByteArray(serializer, array))
    }

    companion object {
        inline fun <reified T : ReloadableState<T>> ExtensionAwareBuilder.stateMemoryProjection(
            key: String,
            initialState: T,
        ): StateMemoryProjection<T> {
            return registerExtension(StateMemoryProjection(key, serializer(), initialState))
        }

        inline fun <reified K, reified V> ExtensionAwareBuilder.mapMemoryProjection(
            key: String,
            initialState: MemoryMapBuffer<K, V> = MemoryMapBuffer(),
        ): StateMemoryProjection<MemoryMapBuffer<K, V>> {
            return registerExtension(StateMemoryProjection(key, serializer(), initialState))
        }
    }

}

interface ReloadableState<T : ReloadableState<T>> {
    fun reset()
    fun reload(value: T)
}

@Serializable
class MemoryMapBuffer<K, V>(
    val map: MutableMap<K, V> = mutableMapOf(),
) : ReloadableState<MemoryMapBuffer<K, V>>, MutableMap<K, V> by map {

    override fun reset() {
        map.clear()
    }

    override fun reload(value: MemoryMapBuffer<K, V>) {
        map.clear()
        map.putAll(value.map)
    }

    override fun toString(): String = "ReloadableMapState(map=$map)"
}