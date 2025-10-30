package dev.helight.krescent.postgres

import dev.helight.krescent.synchronization.KrescentLockProvider
import dev.helight.krescent.test.KrescentLockProviderContract
import org.jetbrains.exposed.sql.Database
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration

@Testcontainers
class PostgresLockTest : KrescentLockProviderContract {

    companion object {

        @Container
        private val postgres = GenericContainer("postgres:latest")
            .withExposedPorts(5432)
            .withEnv("POSTGRES_USER", "root")
            .withEnv("POSTGRES_PASSWORD", "example")
            .withStartupTimeout(Duration.ofSeconds(60))

        private val connectionString get() = "jdbc:postgresql://${postgres.host}:${postgres.getMappedPort(5432)}/root"

        fun connect(): Database = Database.connect(
            url = connectionString,
            driver = "org.postgresql.Driver",
            user = "root",
            password = "example"
        )
    }

    override val latency: Long
        get() = 50L

    override fun getProvider(): KrescentLockProvider {

        return PostgresLockProvider(
            database = connect()
        )
    }
}
