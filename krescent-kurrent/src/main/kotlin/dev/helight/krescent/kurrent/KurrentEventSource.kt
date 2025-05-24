package dev.helight.krescent.kurrent

import dev.helight.krescent.EventMessage
import dev.helight.krescent.EventPublisher
import dev.helight.krescent.StreamingEventSource
import io.kurrent.dbclient.AppendToStreamOptions
import io.kurrent.dbclient.KurrentDBClient
import io.kurrent.dbclient.ReadStreamOptions
import io.kurrent.dbclient.ResolvedEvent
import io.kurrent.dbclient.StreamState
import io.kurrent.dbclient.SubscribeToStreamOptions
import io.kurrent.dbclient.Subscription
import io.kurrent.dbclient.SubscriptionListener
import io.kurrent.dbclient.UserCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

class KurrentEventSource(
    val client: KurrentDBClient,
    val streamId: String,
    val credentials: UserCredentials? = null,
    val resolvedLinks: Boolean = false,
) : StreamingEventSource<KurrentStreamingToken>, EventPublisher {

    private val sendMutex = Mutex()

    override suspend fun deserializeToken(encoded: String): KurrentStreamingToken {
        return when (encoded) {
            "HEAD" -> KurrentStreamingToken.HeadStreamingToken()
            "TAIL" -> KurrentStreamingToken.TailStreamingToken()
            else -> KurrentStreamingToken.RevisionStreamingToken(encoded.toLong())
        }
    }

    override suspend fun getHeadToken(): KurrentStreamingToken {
        return KurrentStreamingToken.HeadStreamingToken()
    }

    override suspend fun getTailToken(): KurrentStreamingToken {
        return KurrentStreamingToken.TailStreamingToken()
    }

    override suspend fun getTokenAtTime(timestamp: Instant): KurrentStreamingToken {
        error("Not implemented for KurrentDB")
    }

    override suspend fun getTokenForEventId(eventId: String): KurrentStreamingToken? {
        error("Not implemented for KurrentDB")
    }

    override suspend fun fetchEventsAfter(
        token: KurrentStreamingToken?,
        limit: Int?,
    ): Flow<Pair<EventMessage, KurrentStreamingToken>> {
        var readStreamOptions = ReadStreamOptions.get().forwards()
        if (credentials != null) readStreamOptions = readStreamOptions.authenticated(credentials)
        if (limit != null) readStreamOptions.maxCount(limit.toLong())
        if (resolvedLinks) readStreamOptions = readStreamOptions.resolveLinkTos()
        readStreamOptions = (token ?: getHeadToken()).applyToReadOption(readStreamOptions)
        val startRevision = if (token is KurrentStreamingToken.RevisionStreamingToken) token.revision else null

        return channelFlow {
            launch(Dispatchers.IO) {
                val result = client.readStream(streamId, readStreamOptions).await()
                for (t in result.events) {
                    val evt = t.originalEvent
                    val token = KurrentStreamingToken.RevisionStreamingToken(evt.revision)
                    val message = KurrentMessageFactory.decode(evt)
                    // The docs say the revision is exclusive, but in my testing it seems inclusive.
                    // So we just check if the revision is the same, and if it is, we skip it.
                    if (startRevision == token.revision) continue
                    send(message to token)
                }
            }.join()
        }
    }

    override suspend fun streamEvents(startToken: KurrentStreamingToken?): Flow<Pair<EventMessage, KurrentStreamingToken>> {
        val token = startToken ?: getHeadToken()
        var subscribeToStreamOptions = SubscribeToStreamOptions.get()
        if (credentials != null) subscribeToStreamOptions = subscribeToStreamOptions.authenticated(credentials)
        if (resolvedLinks) subscribeToStreamOptions = subscribeToStreamOptions.resolveLinkTos()
        subscribeToStreamOptions = token.applyToSubscribeOption(subscribeToStreamOptions)
        val startRevision = if (token is KurrentStreamingToken.RevisionStreamingToken) token.revision else null

        return channelFlow {
            launch(Dispatchers.IO) {
                val subscription = client.subscribeToStream(streamId, object : SubscriptionListener() {
                    override fun onEvent(
                        subscription: Subscription,
                        event: ResolvedEvent,
                    ) {
                        val evt = event.originalEvent
                        val token = KurrentStreamingToken.RevisionStreamingToken(evt.revision)
                        val message = KurrentMessageFactory.decode(evt)
                        if (startRevision == token.revision) return
                        runBlocking {
                            send(message to token)
                        }
                    }
                }, subscribeToStreamOptions).await()
                try {
                    delay(Long.MAX_VALUE)
                } finally {
                    subscription.stop()
                }
            }
        }
    }

    override suspend fun publish(event: EventMessage): Unit = coroutineScope {
        sendMutex.withLock {
            launch(Dispatchers.IO) {
                val eventData = KurrentMessageFactory.encode(event)
                var appendOptions = AppendToStreamOptions.get().streamState(StreamState.AnyStreamState())
                if (credentials != null) appendOptions = appendOptions.authenticated(credentials)

                client.appendToStream(streamId, appendOptions, eventData).await()
            }.join()
        }
    }
}

