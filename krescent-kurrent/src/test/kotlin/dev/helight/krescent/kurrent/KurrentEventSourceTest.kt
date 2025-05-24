package dev.helight.krescent.kurrent

import dev.helight.krescent.EventMessage
import dev.helight.krescent.bufferInMemory
import io.kurrent.dbclient.DeleteStreamOptions
import io.kurrent.dbclient.KurrentDBClient
import io.kurrent.dbclient.KurrentDBConnectionString
import io.kurrent.dbclient.UserCredentials
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Before
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration
import kotlin.test.Test

@Testcontainers
class KurrentEventSourceTest {

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

    private fun runWithTestStream(block: suspend CoroutineScope.(KurrentDBClient, KurrentEventSource) -> Unit) = runBlocking {
        val client = KurrentDBClient.create(
            KurrentDBConnectionString.parseOrThrow(connectionString),
        )
        val eventSource = KurrentEventSource(client, testStreamId, testCredentials)

        try {
            block(client, eventSource)
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

    @Test
    fun `Write and Resolve Events`() = runWithTestStream { client, eventSource ->
        eventSource.publish(EventMessage(type = "my-type", payload = buildJsonObject {
            put("number", 1)
        }))
        eventSource.publish(EventMessage(type = "my-type", payload = buildJsonObject {
            put("number", 2)
        }))
        eventSource.publish(EventMessage(type = "my-type", payload = buildJsonObject {
            put("number", 3)
        }))

        val events = eventSource.fetchEventsAfter(eventSource.getHeadToken()).toList()
        assertEquals(3, events.size)
    }

    @Test
    fun `Write and listen to Events`() = runWithTestStream { client, eventSource ->
        eventSource.publish(EventMessage(type = "my-type", payload = buildJsonObject {
            put("number", 1)
        }))
        eventSource.publish(EventMessage(type = "my-type", payload = buildJsonObject {
            put("number", 2)
        }))
        val buffer = eventSource.streamEvents().bufferInMemory(this)
        delay(100)
        eventSource.publish(EventMessage(type = "my-type", payload = buildJsonObject {
            put("number", 3)
        }))
        delay(100)
        eventSource.publish(EventMessage(type = "my-type", payload = buildJsonObject {
            put("number", 4)
        }))
        delay(500)
        val timeline = buffer.stop()
        println(timeline)
        assertEquals(4, timeline.size)
        assertEquals(1, timeline[0].first.payload.jsonObject["number"]?.jsonPrimitive?.int)
        assertEquals(2, timeline[1].first.payload.jsonObject["number"]?.jsonPrimitive?.int)
        assertEquals(3, timeline[2].first.payload.jsonObject["number"]?.jsonPrimitive?.int)
        assertEquals(4, timeline[3].first.payload.jsonObject["number"]?.jsonPrimitive?.int)
    }

    @Test
    fun `Listen at the tail after events have already been inserted`() = runWithTestStream { client, eventSource ->
        eventSource.publish(EventMessage(type = "my-type", payload = buildJsonObject {
            put("number", 1)
        }))
        eventSource.publish(EventMessage(type = "my-type", payload = buildJsonObject {
            put("number", 2)
        }))
        eventSource.publish(EventMessage(type = "my-type", payload = buildJsonObject {
            put("number", 3)
        }))
        val buffer = eventSource.streamEvents(eventSource.getTailToken()).bufferInMemory(this)
        delay(100)
        eventSource.publish(EventMessage(type = "my-type", payload = buildJsonObject {
            put("number", 4)
        }))
        delay(500)
        val timeline = buffer.stop()
        assertEquals(1, timeline.size)
        assertEquals(4, timeline[0].first.payload.jsonObject["number"]?.jsonPrimitive?.int)
    }

    @Test
    fun `Read after a specific revision`() = runWithTestStream { client, eventSource ->
        eventSource.publish(EventMessage(type = "my-type", payload = buildJsonObject {
            put("number", 1)
        }))
        eventSource.publish(EventMessage(type = "my-type", payload = buildJsonObject {
            put("number", 2)
        }))
        eventSource.publish(EventMessage(type = "my-type", payload = buildJsonObject {
            put("number", 3)
        }))
        eventSource.publish(EventMessage(type = "my-type", payload = buildJsonObject {
            put("number", 4)
        }))
        val first = eventSource.fetchEventsAfter(eventSource.getHeadToken(), 2).toList()
        val buffer = eventSource.fetchEventsAfter(first.last().component2()).toList()
        assertEquals(2, buffer.size)
        assertEquals(3, buffer[0].first.payload.jsonObject["number"]?.jsonPrimitive?.int)
        assertEquals(4, buffer[1].first.payload.jsonObject["number"]?.jsonPrimitive?.int)
    }

}