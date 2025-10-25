package dev.helight.krescent.source

import dev.helight.krescent.event.EventMessage
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class PollingEventSource<T : StoredEventSource>(
    val delegate: T,
    val pollingLimit: Int? = null,
    val pollingDelay: Long = 500L,
) : StreamingEventSource, StoredEventSource by delegate {

    override suspend fun streamEvents(startToken: StreamingToken<*>?): Flow<Pair<EventMessage, StreamingToken<*>>> =
        coroutineScope {
            var currentToken: StreamingToken<*> = startToken ?: delegate.getHeadToken()
            return@coroutineScope flow {
                while (true) {
                    val subFlow = fetchEventsAfter(currentToken, pollingLimit)
                    var counter = 0
                    subFlow.collect {
                        emit(it)
                        currentToken = it.second
                        counter++
                    }
                    val reachedEnd = counter < (pollingLimit ?: Int.MAX_VALUE)
                    if (reachedEnd) {
                        delay(pollingDelay)
                    }
                }
            }
        }

}

fun <T : StoredEventSource> T.asPollingSource(
    pollingLimit: Int? = null,
    pollingDelay: Long = 500L,
): PollingEventSource<T> = PollingEventSource(
    delegate = this,
    pollingLimit = pollingLimit,
    pollingDelay = pollingDelay,
)