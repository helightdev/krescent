package dev.helight.krescent.postgres

import dev.helight.krescent.synchronization.KrescentLock
import dev.helight.krescent.synchronization.KrescentLockProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.*
import kotlin.time.Duration

class PostgresLockProvider(
    val database: Database,
    val leaseTime: Duration = Duration.INFINITE,
) : KrescentLockProvider {

    override suspend fun getLock(identity: String): KrescentLock {
        return PostgresLock(listOf(identity))
    }

    override suspend fun getMultiLock(identities: Collection<String>): KrescentLock {
        return PostgresLock(identities)
    }

    private inner class PostgresLock(
        val identities: Collection<String>,
    ) : KrescentLock {

        private val open = Stack<CompletableDeferred<Unit>>()

        override suspend fun acquire(timeout: Duration?) {
            val releaser = CompletableDeferred<Unit>()
            launchPostgresLockJob(database, identities, releaser, timeout ?: Duration.INFINITE, leaseTime).await()
            open.push(releaser)
        }

        override suspend fun release() {
            val releaser = open.pop() ?: return
            releaser.complete(Unit)
        }
    }
}

private fun hashKey(key: String): Long {
    val bytes = key.toByteArray()
    val digest = MessageDigest.getInstance("MD5").digest(bytes)
    return ByteBuffer.wrap(digest.sliceArray(0..7)).long
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun launchPostgresLockJob(
    database: Database,
    identities: Collection<String>,
    releaser: CompletableDeferred<Unit>,
    timeout: Duration,
    leaseTime: Duration,
): Deferred<Unit> {
    val started = CompletableDeferred<Unit>()
    CoroutineScope(Dispatchers.IO).launch {
        newSuspendedTransaction(db = database) {
            try {
                val setLockTimeout = when (timeout) {
                    Duration.INFINITE -> "SET LOCAL lock_timeout = 0"
                    else -> "SET LOCAL lock_timeout = ${timeout.inWholeMilliseconds}"
                }
                exec(
                    identities.joinToString(
                        prefix = "DO $$ BEGIN $setLockTimeout; ",
                        separator = ";",
                        postfix = "; END $$"
                    ) { "PERFORM pg_advisory_xact_lock(${hashKey(it)})" })
                started.complete(Unit)
            } catch (e: Exception) {
                started.completeExceptionally(e)
                return@newSuspendedTransaction
            }
            select {
                releaser.onAwait {}
                onTimeout(leaseTime) {}
            }
        }
    }
    return started
}