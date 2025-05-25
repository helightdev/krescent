package dev.helight.krescent.models

import dev.helight.krescent.event.Event

/**
 * Represents an extension mechanism for a model, like projections or internal stream state management.
 * This extension will be accessed using a delegate variable that returns the [T] value from the [unpack] method.
 */
interface ModelExtension<T> {
    /**
     * Handles an incoming event, typically for processing or updating the state of a model.
     * You may listen to system events like [dev.helight.krescent.event.SystemStreamHeadEvent]
     * to initialize remote or internal state.
     */
    suspend fun handleEvent(event: Event) {}

    /**
     * Unpacks and retrieves the underlying model or value represented by this extension.
     */
    fun unpack(): T
}

/**
 * An interface for builders that can register model extensions.
 */
interface ExtensionAwareBuilder {

    /**
     * Registers a model extension of type [T] and returns it.
     *
     * @param extension The model extension to register.
     * @return The registered model extension.
     */
    fun <T : ModelExtension<*>> registerExtension(extension: T): T

}