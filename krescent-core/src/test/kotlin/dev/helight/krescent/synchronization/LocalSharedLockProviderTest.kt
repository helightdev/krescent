package dev.helight.krescent.synchronization

import dev.helight.krescent.synchronization.KrescentLockProvider.Extensions.getMultiLock
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class LocalSharedLockProviderTest {

    @Test
    fun `Acquired locks work as expected`() = runBlocking {
        val provider = LocalSharedLockProvider()
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
    fun `Check if lock is shared between identities as expected`() = runBlocking {
        val provider = LocalSharedLockProvider()
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
        assertTrue(timeAfter - timeStart >= 95)
    }

    @Test
    fun `Multilocks work just like normal locks`() = runBlocking {
        val provider = LocalSharedLockProvider()
        val timeStart = System.currentTimeMillis()
        listOf(
            async {
                val lock = provider.getMultiLock("a", "b")
                lock.runGuarded {
                    delay(50)
                }
            },
            async {
                val lock = provider.getMultiLock("x", "y")
                lock.runGuarded {
                    delay(50)
                }
            }
        ).awaitAll()
        val timeAfter = System.currentTimeMillis()
        assertTrue(timeAfter - timeStart >= 95)
    }
}