package dev.helight.krescent.redisson

import dev.helight.krescent.source.EventPublisher
import dev.helight.krescent.source.PollingStreamingEventSource.Companion.pollingWithNotifications
import dev.helight.krescent.source.StreamingEventSource
import dev.helight.krescent.source.impl.InMemoryEventStore
import dev.helight.krescent.test.StreamingEventSourceContract
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration
import java.util.*

@Testcontainers
class RedisNotifierStreamingTest : StreamingEventSourceContract {

    companion object {

        @Container
        private val valkey = GenericContainer("valkey/valkey:latest")
            .withExposedPorts(6379)
            .withStartupTimeout(Duration.ofSeconds(60))

        private val connectionString get() = "redis://${valkey.host}:${valkey.getMappedPort(6379)}"

        fun connect(): RedissonClient = Redisson.create(Config().apply {
            useSingleServer().setAddress(connectionString)
        })
    }

    override fun execWithStreamingSource(block: suspend CoroutineScope.(StreamingEventSource, EventPublisher) -> Unit) {
        val source = InMemoryEventStore()
        val redis = connect()
        val notifier = RedisNotifier(redis, "krescent:notifications:${UUID.randomUUID()}")
        val publisher = notifier.wrap(source)

        val notifications = MutableSharedFlow<Unit>(0)
        val job = notifier.receiveTo(notifications)
        val streaming = source.pollingWithNotifications(notifications)
        try {
            runBlocking {
                this.block(streaming, publisher)
            }
        } finally {
            job.cancel()
        }
    }
}