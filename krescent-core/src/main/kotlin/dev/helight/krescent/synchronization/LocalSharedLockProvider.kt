package dev.helight.krescent.synchronization

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration

/**
 * A [KrescentLockProvider] implementation that provides a shared locking mechanism using a single [Mutex].
 * This ensures that all locks provided by this implementation are sharing the same mutual exclusion.
 */
class LocalSharedLockProvider(
    private val mutex: Mutex = Mutex()
) : KrescentLockProvider {

    override suspend fun getLock(identity: String): KrescentLock {
        return SharedLockReference()
    }

    override suspend fun getMultiLock(identities: Collection<String>): KrescentLock {
        return SharedLockReference()
    }

    private inner class SharedLockReference : KrescentLock{
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

