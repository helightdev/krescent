package dev.helight.krescent.synchronization

import dev.helight.krescent.test.KrescentLockProviderContract
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalLockProviderTest : KrescentLockProviderContract {

    @Test
    fun `getLock should return the same lock for the same identity`() = runBlocking {
        val provider = getProvider()
        val lock1 = provider.getLock("test-identity")
        val lock2 = provider.getLock("test-identity")
        assert(lock1 === lock2) { "Expected the same instance of the lock for the same identity" }
    }

    @Test
    fun `getLock should return a different lock for a different identity`() = runBlocking {
        val provider = getProvider()
        val lock1 = provider.getLock("identity1")
        val lock2 = provider.getLock("identity2")
        assert(lock1 !== lock2) { "Expected a different instance of the lock for different identities" }
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

    override fun getProvider(): KrescentLockProvider = LocalLockProvider()
}