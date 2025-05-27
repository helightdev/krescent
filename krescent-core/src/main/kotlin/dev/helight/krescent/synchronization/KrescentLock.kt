package dev.helight.krescent.synchronization

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.time.Duration

interface KrescentLock {

    /**
     * Acquires the lock, suspending until it is available.
     * If a timeout is specified, it will wait for the specified duration before giving up.
     *
     * The timeout enforcement is implementation-dependent and may be executed locally, remotely or by both parties.
     */
    suspend fun acquire(timeout: Duration? = null)

    /**
     * Releases the lock, allowing others to acquire it.
     */
    suspend fun release()

    /**
     * Runs the given block of code while holding the lock.
     * If a timeout is specified, it will limit how long it waits to acquire the lock before giving up.
     *
     * @see acquire
     * @see release
     */
    suspend fun runGuarded(timeout: Duration? = null, block: suspend () -> Unit) {
        acquire(timeout)
        try {
            block()
        } finally {
            release()
        }
    }

    /**
     * A multi-lock that can acquire and release multiple locks at once.
     * This is useful for scenarios where you need to lock multiple resources together, and the lock provider
     * does not natively support locking multiple resources in a single operation.
     */
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