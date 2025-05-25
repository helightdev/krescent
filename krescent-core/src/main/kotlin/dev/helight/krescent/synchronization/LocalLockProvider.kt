package dev.helight.krescent.synchronization

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.lang.ref.WeakReference
import kotlin.time.Duration

/**
 * A [KrescentLockProvider] implementation that provides unique, reusable locks
 * identified by their string identities. Synchronization is only consistent within
 * the usage of this provider.
 *
 * This implementation uses a [Mutex] for each lock, allowing for concurrent
 * access to the same lock by different coroutines. Unused locks are cleaned up
 * periodically after provider access at most every [cleanupInterval] milliseconds.
 */
class LocalLockProvider(
    val cleanupInterval: Long = 1000,
) : KrescentLockProvider {

    internal val locks = mutableMapOf<String, WeakReference<LocalLockImpl>>()
    private val mainMutex = Mutex()
    private var lastCleanup: Long = System.currentTimeMillis()

    internal suspend fun cleanup() {
        mainMutex.withLock {
            // Remove orphaned locks
            locks.entries.filter {
                it.value.get() == null
            }.forEach {
                locks.remove(it.key)
            }
            lastCleanup = System.currentTimeMillis()
        }
    }

    override suspend fun getLock(identity: String): KrescentLock = mainMutex.withLock {
        // Remove orphaned locks
        if (System.currentTimeMillis() - lastCleanup > cleanupInterval) {
            cleanup()
        }

        // Retrieve an existing lock or create a new one
        val stored = locks[identity]?.get()
        if (stored != null) return@withLock stored
        val newLock = LocalLockImpl()
        locks[identity] = WeakReference(newLock)
        newLock
    }

    internal class LocalLockImpl(
        val mutex: Mutex = Mutex(),
    ) : KrescentLock {
        override suspend fun acquire(timeout: Duration?) {
            if (timeout == null) {
                mutex.lock()
            } else {
                withTimeout(timeout) {
                    mutex.lock()
                }
            }
        }

        override suspend fun release() {
            mutex.unlock()
        }
    }
}