package dev.helight.krescent.redisson

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Testcontainers
class RedisNotifierTest {
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

    @Test
    fun `Receive and publish notifications`(): Unit = runBlocking {
        val client = connect()
        val notifier = RedisNotifier(client)
        val flow = MutableSharedFlow<Unit>(0)
        val job = notifier.receiveTo(flow)
        var hasReceived = false
        launch {
            val channel = flow.produceIn(this)
            channel.receive()
            hasReceived = true
            channel.cancel()
        }
        delay(50)
        assertFalse(hasReceived)
        notifier.notify()
        delay(50)
        MutableSharedFlow<Unit>(0)
        assertTrue(hasReceived)
        job.cancel()
    }
}