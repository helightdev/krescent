package dev.helight.krescent.kurrent

import dev.helight.krescent.source.EventPublisher
import dev.helight.krescent.source.StreamingEventSource
import dev.helight.krescent.test.StreamingEventSourceContract
import io.kurrent.dbclient.DeleteStreamOptions
import io.kurrent.dbclient.KurrentDBClient
import io.kurrent.dbclient.KurrentDBConnectionString
import io.kurrent.dbclient.UserCredentials
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration

@Testcontainers
class KurrentEventSourceTest : StreamingEventSourceContract {

    companion object {

        @Container
        private val kurrent = GenericContainer("docker.kurrent.io/kurrent-latest/kurrentdb:latest")
            .withExposedPorts(2113)
            .withCommand("--insecure", "--run-projections=All", "--enable-atom-pub-over-http")
            .withStartupTimeout(Duration.ofSeconds(60))
        private var testStreamId = "test-stream"
        private val testCredentials = UserCredentials("admin", "changeit")
        private val connectionString get() = "kurrentdb://admin:changeit@localhost:${kurrent.getMappedPort(2113)}?tls=false"

    }

    override fun execWithStreamingSource(block: suspend CoroutineScope.(StreamingEventSource, EventPublisher) -> Unit) =
        runBlocking {
            val client = KurrentDBClient.create(
                KurrentDBConnectionString.parseOrThrow(connectionString),
            )
            val streamName = "kurrent-test-${java.util.UUID.randomUUID()}"
            val eventSource = KurrentEventSource(client, streamName, testCredentials)
            try {
                block(eventSource, eventSource)
            } finally {
                try {
                    client.deleteStream(
                        testStreamId, DeleteStreamOptions.get()
                            .authenticated(testCredentials)
                    ).get()
                    client.shutdown().get()
                } catch (e: Exception) {
                    println("Failed to delete stream $testStreamId: ${e.message}")
                }
            }

        }

}