package dev.helight.krescent.kurrent

import dev.helight.krescent.event.EventMessage
import dev.helight.krescent.source.EventPublisher
import dev.helight.krescent.source.StreamingEventSource
import dev.helight.krescent.source.StreamingToken
import io.kurrent.dbclient.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.future.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Represents an event source for interacting with a KurrentDB stream.
 * This class implements both event consumption via `StreamingEventSource`
 * and event publishing via `EventPublisher`.
 *
 * @property client The KurrentDBClient used for interacting with the database.
 * @property streamId The unique identifier for the stream being referenced.
 * @property credentials Optional user credentials for authentication.
 * @property resolvedLinks Indicates whether links should be resolved.
 */
class KurrentEventSource(
    val client: KurrentDBClient,
    val streamId: String,
    val credentials: UserCredentials? = null,
    val resolvedLinks: Boolean = false,
) : StreamingEventSource, EventPublisher {

    private val sendMutex = Mutex()

    override suspend fun deserializeToken(encoded: String): KurrentStreamingToken {
        return when (encoded) {
            "HEAD" -> KurrentStreamingToken.HeadToken()
            "TAIL" -> KurrentStreamingToken.TailToken()
            else -> KurrentStreamingToken.RevisionToken(encoded.toLong())
        }
    }

    override suspend fun getHeadToken(): KurrentStreamingToken = KurrentStreamingToken.HeadToken()

    override suspend fun getTailToken(): KurrentStreamingToken = KurrentStreamingToken.TailToken()

    override suspend fun fetchEventsAfter(
        token: StreamingToken<*>?,
        limit: Int?,
    ): Flow<Pair<EventMessage, KurrentStreamingToken>> {
        if (token != null && token !is KurrentStreamingToken) {
            throw IllegalArgumentException("Token must be of type KurrentStreamingToken")
        }

        val readStreamOptions = ReadStreamOptions.get().forwards()
        if (credentials != null) readStreamOptions.authenticated(credentials)
        if (limit != null) readStreamOptions.maxCount(limit.toLong())
        if (resolvedLinks) readStreamOptions.resolveLinkTos()
        (token ?: getHeadToken()).applyTo(readStreamOptions)
        val startRevision = if (token is KurrentStreamingToken.RevisionToken) token.revision else null

        return channelFlow {
            launch(Dispatchers.IO) {
                try {
                    val result = client.readStream(streamId, readStreamOptions).await()
                    for (t in result.events) {
                        val evt = t.originalEvent
                        val token = KurrentStreamingToken.RevisionToken(evt.revision)
                        val message = KurrentMessageFactory.decode(evt)
                        // The docs say the revision is exclusive, but in my testing it seems inclusive.
                        // So we just check if the revision is the same, and if it is, we skip it.
                        if (startRevision == token.revision) continue
                        send(message to token)
                    }
                } catch (_: StreamNotFoundException) {
                    // If the stream does not exist, we just return an empty flow since this is not considered an error.
                }
            }.join()
        }
    }

    override suspend fun streamEvents(startToken: StreamingToken<*>?): Flow<Pair<EventMessage, KurrentStreamingToken>> {
        if (startToken != null && startToken !is KurrentStreamingToken) {
            throw IllegalArgumentException("Token must be of type KurrentStreamingToken")
        }

        val token = startToken ?: getHeadToken()
        val subscribeToStreamOptions = SubscribeToStreamOptions.get()
        if (credentials != null) subscribeToStreamOptions.authenticated(credentials)
        if (resolvedLinks) subscribeToStreamOptions.resolveLinkTos()
        token.applyTo(subscribeToStreamOptions)
        val startRevision = if (token is KurrentStreamingToken.RevisionToken) token.revision else null

        return channelFlow {
            // I move this to the IO dispatcher since I don't trust the kurrentdb client after the fromRevision incident.
            launch(Dispatchers.IO) {
                val subscription = client.subscribeToStream(streamId, object : SubscriptionListener() {
                    override fun onEvent(
                        subscription: Subscription,
                        event: ResolvedEvent,
                    ) {
                        val evt = event.originalEvent
                        val token = KurrentStreamingToken.RevisionToken(evt.revision)
                        if (startRevision == token.revision) return
                        val message = KurrentMessageFactory.decode(evt)
                        runBlocking {
                            send(message to token)
                        }
                    }

                    override fun onCancelled(subscription: Subscription, exception: Throwable) {
                        cancel("Subscription error", exception)
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
            val eventData = KurrentMessageFactory.encode(event)
            val appendOptions = AppendToStreamOptions.get().streamState(StreamState.AnyStreamState())
            if (credentials != null) appendOptions.authenticated(credentials)
            client.appendToStream(streamId, appendOptions, eventData).await()
        }
    }

    override suspend fun publishAll(events: List<EventMessage>) {
        sendMutex.withLock {
            val eventData = events.map { KurrentMessageFactory.encode(it) }
            val appendOptions = AppendToStreamOptions.get().streamState(StreamState.AnyStreamState())
            if (credentials != null) appendOptions.authenticated(credentials)
            client.appendToStream(streamId, appendOptions, *eventData.toTypedArray()).await()
        }
    }
}


