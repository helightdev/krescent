package dev.helight.krescent.redisson

import dev.helight.krescent.event.EventMessage
import dev.helight.krescent.source.EventPublisher
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.future.asDeferred
import org.redisson.api.RedissonClient
import org.redisson.api.listener.MessageListener
import kotlin.time.Duration

class RedisNotifier(
    val client: RedissonClient,
    val channel: String = "krescent:notifications",
) {
    suspend fun notify() {
        val topic = client.getTopic(channel)
        topic.publishAsync("").asDeferred().await()
    }

    fun wrap(publisher: EventPublisher): EventPublisher {
        return object : EventPublisher {
            override suspend fun publish(event: EventMessage) {
                publisher.publish(event)
                notify()
            }

            override suspend fun publishAll(events: List<EventMessage>) {
                publisher.publishAll(events)
                notify()
            }
        }
    }

    fun receiveTo(target: FlowCollector<Unit>): Job = CoroutineScope(Dispatchers.Default).launch {
        val topic = client.getTopic(this@RedisNotifier.channel)
        val listenerId = topic.addListenerAsync(String::class.java, MessageListener { _, _ ->
            runBlocking {
                target.emit(Unit)
            }
        }).asDeferred().await()
        try {
            delay(Duration.INFINITE)
        } finally {
            withContext(NonCancellable) {
                topic.removeListenerAsync(listenerId).asDeferred().await()
            }
        }
    }
}