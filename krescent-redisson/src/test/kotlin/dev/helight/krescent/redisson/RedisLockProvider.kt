package dev.helight.krescent.redisson

import dev.helight.krescent.synchronization.KrescentLockProvider
import dev.helight.krescent.test.KrescentLockProviderContract
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration
import java.util.*

@Testcontainers
class RedisLockProviderTest : KrescentLockProviderContract {

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

    override fun getProvider(): KrescentLockProvider {
        val db = connect()
        val uid = UUID.randomUUID().toString()
        return RedisLockProvider(db, "krescent:locks:$uid:")
    }
}