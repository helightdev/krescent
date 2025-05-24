package dev.helight.krescent.models

import dev.helight.krescent.event.Event

interface ModelExtension<T> {
    suspend fun handleEvent(event: Event) {}
    fun unpack(): T
}

interface ExtensionAwareBuilder {

    fun <T : ModelExtension<*>> registerExtension(extension: T): T

}