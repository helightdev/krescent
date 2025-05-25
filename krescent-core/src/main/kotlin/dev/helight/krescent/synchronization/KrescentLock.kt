package dev.helight.krescent.synchronization

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.time.Duration

interface KrescentLock {

    suspend fun acquire(timeout: Duration? = null)
    suspend fun release()
    suspend fun runGuarded(timeout: Duration? = null, block: suspend () -> Unit) {
        acquire(timeout)
        try {
            block()
        } finally {
            release()
        }
    }

    class GenericMultiLock(
        val locks: Collection<KrescentLock>,
    ) : KrescentLock {
        override suspend fun acquire(timeout: Duration?): Unit = coroutineScope {
            locks.map {
                async {
                    it.acquire(timeout)
                }
            }.awaitAll()
        }

        override suspend fun release(): Unit = coroutineScope {
            locks.map {
                async {
                    it.release()
                }
            }.awaitAll()
        }
    }
}