package dev.helight.krescent.synchronization

import dev.helight.krescent.synchronization.KrescentLockProvider.Extensions.getMultiLock
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalLockProviderTest {

    @Test
    fun `getLock should return the same lock for the same identity`() = runBlocking {
        val provider = LocalLockProvider()
        val lock1 = provider.getLock("test-identity")
        val lock2 = provider.getLock("test-identity")
        assert(lock1 === lock2) { "Expected the same instance of the lock for the same identity" }
    }

    @Test
    fun `getLock should return a different lock for a different identity`() = runBlocking {
        val provider = LocalLockProvider()
        val lock1 = provider.getLock("identity1")
        val lock2 = provider.getLock("identity2")
        assert(lock1 !== lock2) { "Expected a different instance of the lock for different identities" }
    }

    @Test
    fun `Acquired locks work as expected`() = runBlocking {
        val provider = LocalLockProvider()
        val timeStart = System.currentTimeMillis()
        listOf(
            async {
                val lock = provider.getLock("test-identity")
                lock.runGuarded {
                    delay(50)
                }
            },
            async {
                val lock = provider.getLock("test-identity")
                lock.runGuarded {
                    delay(50)
                }
            }
        ).awaitAll()
        val timeAfter = System.currentTimeMillis()
        assertTrue(timeAfter - timeStart >= 95)
    }

    @Test
    fun `Locks do not conflict with each other`() = runBlocking {
        val provider = LocalLockProvider()
        val timeStart = System.currentTimeMillis()
        listOf(
            async {
                val lock = provider.getLock("test-identity")
                lock.runGuarded {
                    delay(50)
                }
            },
            async {
                val lock = provider.getLock("another-identity")
                lock.runGuarded {
                    delay(50)
                }
            }
        ).awaitAll()
        val timeAfter = System.currentTimeMillis()
        assertTrue(timeAfter - timeStart < 95)
    }

    @Test
    fun `Unused locks may get collected by the garbage collector`() = runBlocking {
        val provider = LocalLockProvider()
        val timeStart = System.currentTimeMillis()
        val kept = provider.getLock("unused-lock")
        listOf(
            async {
                val lock = provider.getLock("test-identity")
                lock.runGuarded {
                    delay(50)
                }
            },
            async {
                val lock = provider.getLock("another-identity")
                lock.runGuarded {
                    delay(50)
                }
            }
        ).awaitAll()
        val timeAfter = System.currentTimeMillis()
        assertTrue(timeAfter - timeStart < 95)

        System.gc()
        delay(100)
        provider.cleanup()
        assertEquals(provider.locks.size, 1)

        kept.runGuarded {
            // Do nothing
        }
    }

    @Test
    fun `Multilocks work and do not overblock`() = runBlocking {
        val provider = LocalLockProvider()
        val timeStart = System.currentTimeMillis()
        listOf(
            async {
                val lock = provider.getMultiLock("a", "b")
                lock.runGuarded {
                    delay(50)
                }
            },
            async {
                val lock = provider.getMultiLock("a", "c")
                lock.runGuarded {
                    delay(50)
                }
            },
            async {
                val lock = provider.getMultiLock("d", "b")
                lock.runGuarded {
                    delay(50)
                }
            },
            async {
                val lock = provider.getMultiLock("x", "y")
                lock.runGuarded {
                    delay(50)
                }
            },
        ).awaitAll()
        val timeAfter = System.currentTimeMillis()
        assertTrue(timeAfter - timeStart >= 95 && timeAfter - timeStart <= 150)
    }
}