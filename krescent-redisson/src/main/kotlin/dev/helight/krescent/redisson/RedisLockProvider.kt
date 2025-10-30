package dev.helight.krescent.redisson

import dev.helight.krescent.synchronization.KrescentLock
import dev.helight.krescent.synchronization.KrescentLockProvider
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.withTimeout
import org.redisson.api.RLock
import org.redisson.api.RedissonClient
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration

class RedisLockProvider(
    val client: RedissonClient,
    val prefix: String = "krescent:locks:",
    val leaseTime: Duration = Duration.INFINITE,
) : KrescentLockProvider {

    private var lockIdCounter: AtomicLong = AtomicLong(0)

    override suspend fun getLock(identity: String): KrescentLock {
        val rlock = client.getLock(prefix + identity)
        return RedisLockImpl(rlock, lockIdCounter.getAndIncrement())
    }

    override suspend fun getMultiLock(identities: Collection<String>): KrescentLock {
        val lockList = identities.map { identity ->
            client.getLock(prefix + identity)
        }.toTypedArray()
        val multiLock = client.getMultiLock(*lockList)
        return RedisLockImpl(multiLock, lockIdCounter.getAndIncrement())
    }

    private inner class RedisLockImpl(
        val lock: RLock,
        val lockId: Long,
    ) : KrescentLock {

        override suspend fun acquire(timeout: Duration?) {
            if (timeout != null) {
                withTimeout(timeout) { acquire() }
            } else {
                acquire()
            }
        }

        private suspend fun acquire() {
            lock.lockAsync(
                when {
                    leaseTime.isInfinite() -> -1L
                    else -> leaseTime.inWholeMilliseconds
                }, TimeUnit.MILLISECONDS, lockId
            ).asDeferred().await()
        }

        override suspend fun release() {
            lock.unlockAsync(lockId).asDeferred().await()
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as RedisLockImpl
            return lockId == other.lockId
        }

        override fun hashCode(): Int {
            return lockId.hashCode()
        }
    }
}