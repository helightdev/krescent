package dev.helight.krescent.kurrent

import io.kurrent.dbclient.ResolvedEvent

fun interface KurrentSubscriptionStreamFilter {
    fun filter(resolved: ResolvedEvent): Boolean

    companion object {
        fun combine(
            vararg filters: KurrentSubscriptionStreamFilter,
        ): KurrentSubscriptionStreamFilter = KurrentSubscriptionStreamFilter { resolved ->
            filters.all { it.filter(resolved) }
        }

        fun includeStreamNames(
            vararg streamNames: String,
        ): KurrentSubscriptionStreamFilter = KurrentSubscriptionStreamFilter { resolved ->
            streamNames.contains(resolved.event.streamId)
        }

        fun excludeStreamNames(
            vararg streamNames: String,
        ): KurrentSubscriptionStreamFilter = KurrentSubscriptionStreamFilter { resolved ->
            !streamNames.contains(resolved.event.streamId)
        }

        fun streamNameRegex(
            regex: Regex,
        ): KurrentSubscriptionStreamFilter = KurrentSubscriptionStreamFilter { resolved ->
            regex.matches(resolved.event.streamId)
        }

        fun eventTypeRegex(
            regex: Regex,
        ): KurrentSubscriptionStreamFilter = KurrentSubscriptionStreamFilter { resolved ->
            regex.matches(resolved.event.eventType)
        }

        fun excludeSystemEvents() = eventTypeRegex("^[^\\\\$].*".toRegex())
    }
}