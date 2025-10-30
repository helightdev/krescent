package dev.helight.krescent.test

import dev.helight.krescent.synchronization.KrescentLockProvider
import dev.helight.krescent.synchronization.KrescentLockProvider.Extensions.getMultiLock
import dev.helight.krescent.synchronization.LocalLockProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

interface KrescentLockProviderContract {

    fun getProvider(): KrescentLockProvider

    @Test
    fun `Acquired locks work as expected`() = runBlocking {
        val provider = getProvider()
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
        val provider = getProvider()
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