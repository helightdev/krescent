package dev.helight.krescent.source

import dev.helight.krescent.event.EventMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select

/**
 * A `PollingStreamingEventSource` is an implementation of the `StreamingEventSource` interface
 * that provides a polling-based mechanism to stream events from a `StoredEventSource`.
 * It fetches events in batches and optionally uses a notification channel to reduce polling latency.
 *
 * @constructor Creates a new instance of `PollingStreamingEventSource`.
 * @param source The underlying stored event source to fetch events from.
 * @param pollingDelay The delay in milliseconds between polling attempts when no events are available.
 * @param batchSize The number of events to fetch in each polling iteration, null for unlimited.
 * @param notificationFlow An optional channel for receiving notifications to trigger polling immediately.
 */
class PollingStreamingEventSource(
    val source: StoredEventSource,
    val pollingDelay: Long = 500,
    val batchSize: Int? = null,
    val notificationFlow: Flow<*>? = null,
) : StreamingEventSource, StoredEventSource by source {

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun streamEvents(startToken: StreamingToken<*>?): Flow<Pair<EventMessage, StreamingToken<*>>> =
        coroutineScope {
            val initialToken = startToken ?: getHeadToken()
            var cursor: StreamingToken<*> = initialToken
            return@coroutineScope channelFlow {
                val channel = notificationFlow?.produceIn(this)
                try {
                    while (isActive) {
                        var hadData = false
                        fetchEventsAfter(cursor, batchSize).collect {
                            cursor = it.second
                            send(it)
                            hadData = true
                        }
                        if (!hadData) {
                            select {
                                onTimeout(pollingDelay) {}
                                channel?.onReceive {}
                            }
                        }
                    }
                } finally {
                    channel?.cancel()
                }
            }
        }

    companion object {
        /**
         * Creates a polling event source that fetches events after a delay.
         *
         * @param pollingDelay The delay in milliseconds between polling attempts, defaults to 500 milliseconds.
         * @param batchSize The maximum number of events to fetch in each polling attempt, or null for no limit.
         */
        fun StoredEventSource.polling(pollingDelay: Long = 500, batchSize: Int? = null) = PollingStreamingEventSource(
            source = this,
            pollingDelay = pollingDelay,
            batchSize = batchSize,
        )

        /**
         * Creates a `PollingStreamingEventSource` that streams events from the `StoredEventSource` using periodic polling
         * and delivers notifications through a specified channel.
         *
         * @param notifications The channel used to send notifications when new events are available.
         * @param pollingDelay The delay in milliseconds between polling attempts. Defaults to 30,000 milliseconds (30 seconds).
         * @param batchSize The maximum number of events to fetch in each polling operation. If null, no limit is applied.
         */
        fun StoredEventSource.pollingWithNotifications(
            notifications: Flow<*>,
            pollingDelay: Long = 30_000L,
            batchSize: Int? = null,
        ) =
            PollingStreamingEventSource(
                source = this,
                pollingDelay = pollingDelay,
                batchSize = batchSize,
                notificationFlow = notifications
            )

    }
}