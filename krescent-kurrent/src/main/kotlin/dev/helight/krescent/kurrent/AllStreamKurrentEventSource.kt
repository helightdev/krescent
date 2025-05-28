package dev.helight.krescent.kurrent

import dev.helight.krescent.event.EventMessage
import dev.helight.krescent.source.StreamingEventSource
import dev.helight.krescent.source.StreamingToken
import io.kurrent.dbclient.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.future.await
import org.intellij.lang.annotations.Language

/**
 * Represents an event source for streaming events from the $all stream in KurrentDB.
 *
 * You can set up server-side filtering using the [filter] builder block, which allows you to specify
 * a custom filter for the events being streamed, see [Server-Side-Filtering](https://docs.kurrent.io/clients/grpc/subscriptions.html#server-side-filtering).
 *
 * Beware that the $all stream will contain events from streams that are technically already deleted or truncated. While
 * in practice this will not be that large of an issue, you can influence the events that are actually passed on to
 * the event handlers by specifying an additional client-side [streamFilter].
 *
 * @property client The KurrentDBClient used for interacting with the database.
 * @property credentials Optional user credentials for authentication.
 * @property streamFilter Optional client-side filter for the events being streamed.
 * @property filter Optional server-side filter for the events being streamed.
 */
class AllStreamKurrentEventSource(
    val client: KurrentDBClient,
    val credentials: UserCredentials? = null,
    val streamFilter: KurrentSubscriptionStreamFilter? = null,
    val filter: (SubscriptionFilterBuilder.() -> Unit)? = null,
) : StreamingEventSource {

    companion object {
        /**
         * @see [AllStreamKurrentEventSource]
         */
        fun eventTypePrefix(
            client: KurrentDBClient,
            vararg prefix: String,
            credentials: UserCredentials? = null,
            streamFilter: KurrentSubscriptionStreamFilter? = null,
        ): AllStreamKurrentEventSource = AllStreamKurrentEventSource(client, credentials, streamFilter, filter = {
            prefix.forEach { addEventTypePrefix(it) }
        })

        /**
         * @see [AllStreamKurrentEventSource]
         */
        fun streamNamePrefix(
            client: KurrentDBClient,
            vararg prefix: String,
            credentials: UserCredentials? = null,
            streamFilter: KurrentSubscriptionStreamFilter? = null,
        ): AllStreamKurrentEventSource = AllStreamKurrentEventSource(client, credentials, streamFilter, filter = {
            prefix.forEach { addStreamNamePrefix(it) }
        })

        /**
         * @see [AllStreamKurrentEventSource]
         */
        fun streamNameRegex(
            client: KurrentDBClient,
            @Language("RegExp") regex: String,
            credentials: UserCredentials? = null,
            streamFilter: KurrentSubscriptionStreamFilter? = null,
        ): AllStreamKurrentEventSource = AllStreamKurrentEventSource(client, credentials, streamFilter, filter = {
            withStreamNameRegularExpression(regex)
        })

        /**
         * @see [AllStreamKurrentEventSource]
         */
        fun eventTypeRegex(
            client: KurrentDBClient,
            @Language("RegExp") regex: String,
            credentials: UserCredentials? = null,
            streamFilter: KurrentSubscriptionStreamFilter? = null,
        ): AllStreamKurrentEventSource = AllStreamKurrentEventSource(client, credentials, streamFilter, filter = {
            withEventTypeRegularExpression(regex)
        })
    }

    override suspend fun getHeadToken(): KurrentLogPositionToken = KurrentLogPositionToken.HeadToken()

    override suspend fun getTailToken(): KurrentLogPositionToken = KurrentLogPositionToken.TailToken()

    override suspend fun deserializeToken(encoded: String): KurrentLogPositionToken = when (encoded) {
        "HEAD" -> KurrentLogPositionToken.HeadToken()
        "TAIL" -> KurrentLogPositionToken.TailToken()
        else -> KurrentLogPositionToken.decodePosition(encoded)
    }

    override suspend fun fetchEventsAfter(
        token: StreamingToken<*>?,
        limit: Int?,
    ): Flow<Pair<EventMessage, StreamingToken<*>>> {
        return when (limit) {
            null -> subscribeToAll(token, cancelOnCaughtUp = true)
            else -> subscribeToAll(token, cancelOnCaughtUp = true).take(limit)
        }
    }

    override suspend fun streamEvents(startToken: StreamingToken<*>?): Flow<Pair<EventMessage, StreamingToken<*>>> {
        return subscribeToAll(startToken)
    }

    private suspend fun subscribeToAll(
        startToken: StreamingToken<*>?,
        cancelOnCaughtUp: Boolean = false,
    ): Flow<Pair<EventMessage, StreamingToken<*>>> {
        if (startToken != null && startToken !is KurrentLogPositionToken) {
            throw IllegalArgumentException("Token must be of type KurrentStreamingToken")
        }

        val token = startToken ?: getHeadToken()
        val subscribeToAllOptions = SubscribeToAllOptions.get()
        if (credentials != null) subscribeToAllOptions.authenticated(credentials)
        token.applyTo(subscribeToAllOptions)
        if (filter != null) {
            subscribeToAllOptions.filter(SubscriptionFilter.newBuilder().apply {
                filter()
            }.build())
        }

        val startPosition = if (token is KurrentLogPositionToken.PositionToken) token.position else null
        return channelFlow {
            launch(Dispatchers.IO) {
                val subscription = client.subscribeToAll(object : SubscriptionListener() {
                    override fun onEvent(
                        subscription: Subscription,
                        event: ResolvedEvent,
                    ) {
                        val evt = event.originalEvent
                        val token = KurrentLogPositionToken.PositionToken(evt.position)
                        if (startPosition == token.position) return
                        if (streamFilter != null && !streamFilter.filter(event)) return
                        val message = KurrentMessageFactory.decode(evt)
                        runBlocking {
                            send(message to token)
                        }
                    }

                    override fun onCancelled(subscription: Subscription, exception: Throwable) {
                        cancel("Subscription error", exception)
                    }

                    override fun onCaughtUp(subscription: Subscription?) {
                        if (cancelOnCaughtUp) {
                            cancel("Caught up to the stream", null)
                        }
                    }
                }, subscribeToAllOptions).await()
                try {
                    delay(Long.MAX_VALUE)
                } finally {
                    subscription.stop()
                }
            }
        }
    }
}

