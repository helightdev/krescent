package dev.helight.krescent.model.projection

import dev.helight.krescent.checkpoint.CheckpointBucket
import dev.helight.krescent.checkpoint.CheckpointSupport
import dev.helight.krescent.event.Event
import dev.helight.krescent.event.EventStreamProcessor
import dev.helight.krescent.event.SystemStreamHeadEvent
import dev.helight.krescent.model.ExtensionAwareBuilder
import dev.helight.krescent.model.ModelExtension
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.serializer

/**
 * A projector that maintains a reloadable state in memory, which can be serialized using kotlinx.serialization.
 *
 * This projector is designed to be used with a [ReloadableState] implementation like [MemoryMapBuffer].
 *
 * @property key The unique key for the state in the checkpoint bucket.
 * @property serializer The serializer for the state type.
 * @property state The initial state that will be managed by this projector.
 */
@OptIn(ExperimentalSerializationApi::class)
class StateMemoryProjector<T : ReloadableState<T>>(
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
        bucket.put(key, Cbor.Default.encodeToByteArray(serializer, state))
    }

    override suspend fun restoreCheckpoint(bucket: CheckpointBucket) {
        val array = bucket.getByteArray(key) ?: error("No checkpoint found for key $key")
        state.reload(Cbor.Default.decodeFromByteArray(serializer, array))
    }

    companion object {
        inline fun <reified T : ReloadableState<T>> ExtensionAwareBuilder.stateMemoryProjection(
            key: String,
            initialState: T,
        ): StateMemoryProjector<T> {
            return registerExtension(StateMemoryProjector(key, serializer(), initialState))
        }

        inline fun <reified K, reified V> ExtensionAwareBuilder.mapMemoryProjection(
            key: String,
            initialState: MemoryMapBuffer<K, V> = MemoryMapBuffer(),
        ): StateMemoryProjector<MemoryMapBuffer<K, V>> {
            return registerExtension(StateMemoryProjector(key, serializer(), initialState))
        }
    }
}


/**
 * Interface for a reloadable state used by projectors like [StateMemoryProjector].
 */
interface ReloadableState<T : ReloadableState<T>> {
    /**
     * Resets the state to its initial state.
     */
    fun reset()

    /**
     * Reloads the state with the provided value.
     */
    fun reload(value: T)
}

/**
 * A mutable map buffer that implements [ReloadableState] for use in memory projections.
 */
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