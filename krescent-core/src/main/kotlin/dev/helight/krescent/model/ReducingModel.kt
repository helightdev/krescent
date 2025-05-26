@file:Suppress("unused")

package dev.helight.krescent.model

import dev.helight.krescent.checkpoint.CheckpointBucket
import dev.helight.krescent.checkpoint.CheckpointSupport
import dev.helight.krescent.event.Event
import dev.helight.krescent.event.EventCatalog
import dev.helight.krescent.model.ReducingModel.StateContext
import dev.helight.krescent.source.EventPublisher
import dev.helight.krescent.source.StreamingEventSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

interface ReducingModel<S: Any> {
    val initialState: S
    var currentState: S

    suspend fun reduce(state: S, event: Event): S

    fun S.push(): S {
        currentState = this
        return currentState
    }

    fun pushState(state: S): S {
        currentState = state
        return currentState
    }

    class StateContext<S>(
        val state: S,
    )

    class CheckpointExtension<S: Any>(
        val model: ReducingModel<S>,
    ) : ModelExtension<Unit>, CheckpointSupport {

        override fun unpack() = Unit

        override suspend fun createCheckpoint(bucket: CheckpointBucket) {
            val serializer = Json.Default.serializersModule.serializer(model.currentState::class.java)
            bucket["state"] = Json.Default.encodeToJsonElement(serializer, model.currentState)
        }

        @Suppress("UNCHECKED_CAST")
        override suspend fun restoreCheckpoint(bucket: CheckpointBucket) {
            val data = bucket["state"] ?: error("No recorded state found in checkpoint bucket")
            val serializer = Json.Default.serializersModule.serializer(model.currentState::class.java)
            model.currentState = Json.Default.decodeFromJsonElement(serializer, data) as S
        }
    }
}

inline fun <M : ReducingModel<S>, S, R> M.useState(block: StateContext<S>.() -> R): R {
    return block(StateContext(currentState))
}


abstract class ReducingWriteModel<S: Any>(
    namespace: String,
    revision: Int,
    catalog: EventCatalog,
    source: StreamingEventSource,
    publisher: EventPublisher? = null,
    configure: suspend EventModelBuilder.() -> Unit = { }
) : WriteModelBase(namespace, revision, catalog, source, publisher, configure), ReducingModel<S> {

    init {
        registerExtension(ReducingModel.CheckpointExtension<S>(this))
    }

    override var currentState: S = initialState
    override suspend fun process(event: Event) {
        currentState = reduce(currentState, event)
    }
}

abstract class ReducingReadModel<S: Any>(
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