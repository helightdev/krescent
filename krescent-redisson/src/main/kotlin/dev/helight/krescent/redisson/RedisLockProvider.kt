package dev.helight.krescent.redisson

import dev.helight.krescent.synchronization.KrescentLock
import dev.helight.krescent.synchronization.KrescentLockProvider
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.withTimeout
import org.redisson.api.RLock
import org.redisson.api.RedissonClient
import java.util.*
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
        return RedisLockImpl(rlock)
    }

    override suspend fun getMultiLock(identities: Collection<String>): KrescentLock {
        val lockList = identities.map { identity ->
            client.getLock(prefix + identity)
        }.toTypedArray()
        val multiLock = client.getMultiLock(*lockList)
        return RedisLockImpl(multiLock)
    }

    private inner class RedisLockImpl(
        val lock: RLock,
    ) : KrescentLock {

        private val claims: Stack<Long> = Stack()

        override suspend fun acquire(timeout: Duration?) {
            if (timeout != null) {
                withTimeout(timeout) { acquire() }
            } else {
                acquire()
            }
        }

        private suspend fun acquire() {
            val claimId = lockIdCounter.getAndIncrement()
            lock.lockAsync(
                when {
                    leaseTime.isInfinite() -> -1L
                    else -> leaseTime.inWholeMilliseconds
                }, TimeUnit.MILLISECONDS, claimId
            ).asDeferred().await()
            claims.push(claimId)
        }

        override suspend fun release() {
            if (claims.isEmpty()) return
            val claimId = claims.pop()
            lock.unlockAsync(claimId).asDeferred().await()
        }
    }
}

