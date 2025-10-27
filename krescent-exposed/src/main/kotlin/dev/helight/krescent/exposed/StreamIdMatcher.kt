package dev.helight.krescent.exposed

import dev.helight.krescent.event.Event
import dev.helight.krescent.event.EventCatalog
import kotlin.reflect.KClass

enum class StreamIdMatcher {
    EQ,
    LIKE,
    REGEX
}

class StreamEventFilter(
    val eventNames: Set<String>,
) {
    companion object {
        fun fromRegex(eventCatalog: EventCatalog, regex: Regex): StreamEventFilter {
            val includes = eventCatalog.events.keys.filter { regex.matches(it) }.toSet()
            return StreamEventFilter(includes)
        }

        fun fromTypes(eventCatalog: EventCatalog, vararg types: KClass<out Event>): StreamEventFilter {
            val includes = types.map {
                eventCatalog.eventTypes[it]?.eventName
                    ?: error("${it.simpleName} is not present in the given event catalog")
            }.toSet()
            return StreamEventFilter(includes)
        }
    }

}