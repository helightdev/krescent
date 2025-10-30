package dev.helight.krescent.test

import dev.helight.krescent.synchronization.KrescentLockProvider
import dev.helight.krescent.synchronization.KrescentLockProvider.Extensions.getMultiLock
import kotlinx.coroutines.*
import org.junit.jupiter.api.Assertions.assertEquals
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.DurationUnit
import kotlin.time.toDuration

interface KrescentLockProviderContract {

    fun getProvider(): KrescentLockProvider
    val latency: Long
        get() = 50L

    @Test
    fun `Acquired locks work as expected`() = runBlocking {
        val provider = getProvider()
        System.currentTimeMillis()
        val resource = LockResource()
        listOf(
            async {
                val lock = provider.getLock("test-identity")
                lock.runGuarded {
                    resource.claim()
                    delay(latency)
                    resource.release()
                }
            },
            async {
                val lock = provider.getLock("test-identity")
                lock.runGuarded {
                    resource.claim()
                    delay(latency)
                    resource.release()
                }
            }
        ).awaitAll()
        return@runBlocking
    }

    @Test
    fun `Reused lock instance still works and is not reentrant`() = runBlocking {
        val provider = getProvider()
        val resource = LockResource()
        val lock = provider.getLock("test-identity")
        listOf(
            async {
                lock.runGuarded {
                    resource.claim()
                    delay(latency)
                    resource.release()
                }
            },
            async {
                lock.runGuarded {
                    resource.claim()
                    delay(latency)
                    resource.release()
                }
            }
        ).awaitAll()
        return@runBlocking
    }


    @Test
    fun `Locks do not conflict with each other`() = runBlocking {
        val provider = getProvider()
        val timeStart = System.currentTimeMillis()
        listOf(
            async {
                val lock = provider.getLock("test-identity")
                lock.runGuarded {
                    delay(latency)
                }
            },
            async {
                val lock = provider.getLock("another-identity")
                lock.runGuarded {
                    delay(latency)
                }
            },
        ).awaitAll()
        val timeAfter = System.currentTimeMillis()
        assertTrue(timeAfter - timeStart < latency * 2)
    }

    @Test
    fun `Test lock timeouts`() = runBlocking {
        val provider = getProvider()
        val lock = provider.getLock("timeout-identity")
        lock.acquire()
        var timeoutThrown = false
        withTimeout(1000L) {
            try {
                lock.acquire(latency.toDuration(DurationUnit.MILLISECONDS))
            } catch (e: Exception) {
                timeoutThrown = true
            }
            println("Acquired lock after timeout period")
        }
        assertTrue(timeoutThrown, "Timeout was not thrown when expected")
    }

    @Test
    fun `Multilocks work and do not overblock`() = runBlocking {
        val provider = getProvider()
        val aRes = LockResource()
        val bRes = LockResource()
        val cRes = LockResource()
        val dRes = LockResource()
        listOf(
            async {
                val lock = provider.getMultiLock("a", "b")
                lock.runGuarded {
                    aRes.claim()
                    bRes.claim()
                    delay(latency)
                    bRes.release()
                    aRes.release()
                }
            },
            async {
                val lock = provider.getMultiLock("a", "c")
                lock.runGuarded {
                    aRes.claim()
                    cRes.claim()
                    delay(latency)
                    cRes.release()
                    aRes.release()
                }
            },
            async {
                val lock = provider.getMultiLock("d", "b")
                lock.runGuarded {
                    dRes.claim()
                    bRes.claim()
                    delay(latency)
                    bRes.release()
                    dRes.release()
                }
            },
            async {
                val lock = provider.getMultiLock("x", "y")
                lock.runGuarded {
                    delay(latency)
                }
            },
        ).awaitAll()
        return@runBlocking
    }

    class LockResource {
        private val atomic: AtomicInteger = AtomicInteger(1)

        fun claim() {
            val v = atomic.decrementAndGet()
            assertEquals(v, 0, "Resource is already claimed!")
        }

        fun release() {
            val v = atomic.incrementAndGet()
            assertEquals(v, 1, "Resource was not claimed!")
        }
    }
}