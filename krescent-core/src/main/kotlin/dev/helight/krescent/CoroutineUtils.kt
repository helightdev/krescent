package dev.helight.krescent

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.atomics.ExperimentalAtomicApi

fun <T> Flow<T>.bufferInMemory(scope: CoroutineScope): FlowBuffer<T> {
    return FlowBuffer<T>().apply {
        start(scope, this@bufferInMemory)
    }
}

class FlowBuffer<T> {
    private val buffer = mutableListOf<T>()
    private var job: Job? = null

    fun start(scope: CoroutineScope, flow: Flow<T>) {
        job = scope.launch {
            flow.collect {
                handle(it)
            }
        }
    }

    private fun handle(value: T) {
        buffer.add(value)
    }

    fun stop(): List<T> {
        job?.cancel()
        job = null
        return buffer
    }
}

/**
 * Joins two flows sequentially, ensuring that the first flow is fully consumed before the second flow is sent.
 * The second flow will still be buffered until it the first flow is fully sent.
 *
 * @param first The first flow to collect
 * @param second The second flow to collect
 * @param activateCallback A callback to be called when both flows are collecting values
 */
@OptIn(ExperimentalAtomicApi::class)
fun <T> joinSequentialFlows(
    first: Flow<T>,
    second: Flow<T>,
    activateCallback: () -> Unit = { },
): Flow<T> = channelFlow {
    val mutex = Mutex()
    val secondBuffer = ArrayDeque<T>()
    var switchToLive = false

    val firstJob = launch {
        first.collect { value ->
            send(value)
        }
    }
    val secondJob = launch {
        second.collect { value ->
            mutex.withLock {
                if (switchToLive) {
                    send(value)
                } else {
                    secondBuffer.add(value)
                }
            }
        }
    }
    activateCallback()
    firstJob.join()
    mutex.withLock {
        while (secondBuffer.isNotEmpty()) {
            send(secondBuffer.removeFirst())
        }
        switchToLive = true
    }
    secondJob.join()
}

/**
 * Runs a sequence of tasks where each task is executed in order without interruption due to exceptions.
 * If any task throws an exception, it collects all exceptions and throws a single `UninterruptedChainException` with a list of errors.
 *
 * @param tasks A list of suspending tasks to be executed. Each task is represented as a suspendable unit function.
 * If any task throws an exception, it is caught and stored for reporting.
 */
suspend fun runUninterruptedChain(
    tasks: List<suspend () -> Unit>,
) {
    val errorBuffer = mutableListOf<Throwable>()
    for (task in tasks) {
        try {
            task()
        } catch (e: Throwable) {
            errorBuffer.add(e)
        }
    }
    if (errorBuffer.isNotEmpty()) throw UninterruptedChainException(errorBuffer)
}

class UninterruptedChainException(
    errors: List<Throwable>,
) : RuntimeException(
    "Uninterrupted chain failed with ${errors.size} errors: ${errors.joinToString("\n") { it.message ?: "Unknown error" }}"
)
