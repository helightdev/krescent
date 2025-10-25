@file:Suppress("unused")

package dev.helight.krescent.model

import dev.helight.krescent.checkpoint.CheckpointBucket
import dev.helight.krescent.checkpoint.CheckpointSupport
import dev.helight.krescent.event.Event
import dev.helight.krescent.event.EventCatalog
import dev.helight.krescent.model.ReducingModel.StateContext
import dev.helight.krescent.source.EventPublisher
import dev.helight.krescent.source.StreamingEventSource
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

interface ReducingModel<S : Any> {
    /**
     * Initial state of this model that will be used before the first event in the stream.
     */
    val initialState: S

    /**
     * The current state of this model, which is updated by the [reduce] function and the checkpointing mechanism.
     */
    var currentState: S

    /**
     * The [KSerializer] used to serialize the state [S] of this model.
     * Per default, this uses reflections to find the proper serializer for the current state class.
     */
    val serializer: KSerializer<Any>
        get() = Json.serializersModule.serializer(currentState::class.java)

    /**
     * Reduces the current state with the given event and returns the new state.
     */
    suspend fun reduce(state: S, event: Event): S

    /**
     * Sets this state as the current state of the enclosing [ReducingModel].
     */
    fun S.push(): S {
        currentState = this
        return currentState
    }

    /**
     * Sets the given [state] as the current state of the enclosing [ReducingModel].
     */
    fun pushState(state: S): S {
        currentState = state
        return currentState
    }

    /**
     * Wraps the current state in a [StateContext] so you can use it like a variable called `state` in the block.
     */
    class StateContext<S>(val state: S)

    class CheckpointExtension<S : Any>(
        val model: ReducingModel<S>,
    ) : ModelExtension<Unit>, CheckpointSupport {

        override fun unpack() = Unit

        override suspend fun createCheckpoint(bucket: CheckpointBucket) {
            val serializer = model.serializer
            bucket["state"] = Json.encodeToJsonElement(serializer, model.currentState)
        }

        @Suppress("UNCHECKED_CAST")
        override suspend fun restoreCheckpoint(bucket: CheckpointBucket) {
            val data = bucket["state"] ?: error("No recorded state found in checkpoint bucket")
            val serializer = model.serializer
            model.currentState = Json.decodeFromJsonElement(serializer, data) as S
        }
    }
}

/**
 * Gives access to the current state of the [ReducingModel] inside of [block] as `state`.
 */
inline fun <M : ReducingModel<S>, S, R> M.useState(block: StateContext<S>.() -> R): R {
    return block(StateContext(currentState))
}


/**
 * Write model implementation of [ReducingModel].
 * @see ReducingModel
 */
abstract class ReducingWriteModel<S : Any>(
    namespace: String,
    revision: Int,
    catalog: EventCatalog,
    source: StreamingEventSource? = null,
    publisher: EventPublisher? = null,
    configure: suspend EventModelBuilder.() -> Unit = { },
) : WriteModelBase(namespace, revision, catalog, source, publisher, configure), ReducingModel<S> {

    init {
        registerExtension(ReducingModel.CheckpointExtension<S>(this))
    }

    override var currentState: S = initialState
    override suspend fun process(event: Event) {
        currentState = reduce(currentState, event)
    }
}

/**
 * Read model implementation of [ReducingModel].
 * @see ReducingModel
 */
abstract class ReducingReadModel<S : Any>(
    namespace: String,
    revision: Int,
    catalog: EventCatalog,
    configure: suspend EventModelBuilder.() -> Unit = { },
) : ReadModelBase(namespace, revision, catalog, configure), ReducingModel<S> {

    init {
        registerExtension(ReducingModel.CheckpointExtension<S>(this))
    }

    override var currentState: S = initialState
    override suspend fun process(event: Event) {
        currentState = reduce(currentState, event)
    }
}