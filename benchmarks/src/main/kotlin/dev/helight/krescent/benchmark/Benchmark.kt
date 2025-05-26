package dev.helight.krescent.benchmark

import com.mongodb.kotlin.client.coroutine.MongoClient
import dev.helight.krescent.checkpoint.FixedEventRateCheckpointStrategy
import dev.helight.krescent.checkpoint.FixedTimeRateCheckpointStrategy
import dev.helight.krescent.checkpoint.impl.InMemoryCheckpointStorage
import dev.helight.krescent.kurrent.KurrentEventSource
import dev.helight.krescent.model.EventModelBase.Extension.withConfiguration
import dev.helight.krescent.model.ReadModelBase.Extension.catchup
import dev.helight.krescent.model.projection.MemoryMapBuffer
import dev.helight.krescent.mongo.MongoCheckpointStorage
import dev.helight.krescent.source.impl.InMemoryEventStore
import io.kurrent.dbclient.KurrentDBClient
import io.kurrent.dbclient.KurrentDBConnectionString
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.math.roundToInt
import kotlin.system.measureTimeMillis
import kotlin.time.DurationUnit
import kotlin.time.toDuration

fun main() = runBlocking {
    val source = InMemoryEventStore()
    val count = 1_000_000

    val file = File("benchmark_events_$count.json")
    if (file.exists()) {
        source.load(file.readBytes())
        println("Loaded $count events from ${file.absolutePath}")
    } else {
        val durationProduce = measureTimeMillis {
            produceMockEventStream(count).collect {
                source.publish(documentEventCatalog.create(it))
            }
        }
        println("Generated $count events in $durationProduce ms")
        file.writeBytes(source.serialize())
        println("Saved events to ${file.absolutePath}")
    }

    val buffer = MemoryMapBuffer<String, MutableMap<String, String>>()
    val durationReplay = computeAverageTime(10) {
        AllDocumentsReadModel(buffer).catchup(source)
    }
    println("Replayed $count events in $durationReplay ms (End buffer size: ${buffer.size})")

    val checkpointDurationReplay = computeAverageTime(10) {
        val storage = InMemoryCheckpointStorage()
        AllDocumentsReadModel(buffer).withConfiguration {
            useCheckpoints(storage, FixedEventRateCheckpointStrategy(100))
        }.catchup(source)
    }
    println("Replayed with checkpointing $count events in $checkpointDurationReplay ms (End buffer size: ${buffer.size})")
    buffer.clear()

    val clientSettings = KurrentDBConnectionString.parseOrThrow("kurrentdb://admin:changeit@localhost:2113?tls=false")
    val client = KurrentDBClient.create(clientSettings)
    val kurrentSource = KurrentEventSource(
        client = client,
        streamId = "benchmark_stream",
    )
    if (kurrentSource.fetchEventsAfter(limit = 10).toList().isNotEmpty()) {
        println("Stream already exists, keeping it...")
    } else {
        println("KurrentDB client created, stream ID: ${kurrentSource.streamId}, publishing to KurrentDB...")
        source.fetchEventsAfter().map { it.first }.toList().windowed(500, 500).forEachIndexed { i, it ->
            kurrentSource.publishAll(it)
            println("Published batch ${i + 1}/${count / 500} of ${it.size} events to KurrentDB")
        }
    }
    println("Published $count events to KurrentDB, checking replay...")
    val kurrentDurationReplay = computeAverageTime(2) {
        AllDocumentsReadModel(buffer).catchup(kurrentSource)
    }
    println("Replayed $count events from KurrentDB in $kurrentDurationReplay ms (End buffer size: ${buffer.size})")
    printStats("KurrentDB", count, kurrentDurationReplay)

    val mongoClient = MongoClient.create("mongodb://root:example@localhost:27017/")
    val mongoDatabase = mongoClient.getDatabase("test")
    val mongoCheckpointStorage = MongoCheckpointStorage(mongoDatabase)
    mongoCheckpointStorage.clearCheckpoints()

    val mongoDurationReplay = computeAverageTime(1) {
        AllDocumentsReadModelMongo("benchmark_projection", mongoDatabase).catchup(source)
    }
    printStats("MongoDB", count, mongoDurationReplay)

    val mongoDurationSnapshottingReplay = computeAverageTime(1) {
        AllDocumentsReadModelMongo("benchmark_projection", mongoDatabase).withConfiguration {
            useCheckpoints(mongoCheckpointStorage, FixedTimeRateCheckpointStrategy(10.toDuration(DurationUnit.SECONDS)))
        }.catchup(source)
    }
    printStats("MongoDB Snapshotting", count, mongoDurationSnapshottingReplay)
}

suspend fun computeAverageTime(count: Int, block: suspend () -> Unit): Double {
    val timings = mutableListOf<Long>()
    repeat(count) {
        timings.add(measureTimeMillis {
            block()
        })
        println("Iteration ${it + 1} completed in average ${timings.last()} ms")
    }
    return timings.average()
}

fun printStats(name: String, count: Int, duration: Double) {
    val eps = count * 1000 / duration
    println("$name: Processed $count events in $duration ms (${(duration / 1000 / 60).roundToInt()}min), $eps events/sec")
}