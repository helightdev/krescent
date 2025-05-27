package dev.helight.krescent.synchronization

import dev.helight.krescent.event.Event
import dev.helight.krescent.event.EventStreamProcessor
import dev.helight.krescent.event.SystemHintBeginTransactionEvent
import dev.helight.krescent.event.SystemHintEndTransactionEvent
import dev.helight.krescent.model.ExtensionAwareBuilder
import dev.helight.krescent.model.ModelExtension
import kotlin.time.Duration

class ModelLockTransactionHandler(
    val lock: KrescentLock,
    val duration: Duration?,
) : ModelExtension<ModelLockTransactionHandler>, EventStreamProcessor {

    private var isInLockTransaction = false

    @Suppress("unused")
    val inTransaction: Boolean
        get() = isInLockTransaction

    override fun unpack() = this
    override suspend fun process(event: Event) {
        when (event) {
            is SystemHintBeginTransactionEvent -> {
                if (!isInLockTransaction) {
                    isInLockTransaction = true
                    lock.acquire(duration)
                }
            }
            is SystemHintEndTransactionEvent -> {
                if (isInLockTransaction) {
                    isInLockTransaction = false
                    lock.release()
                }
            }
        }
    }

    object Extensions {

        /**
         * Establish a transaction boundary for this event model using the [provider] to acquire a lock.
         * If the lock isn't acquired within the specified [duration], it will give up throwing an exception.
         */
        @Suppress("unused")
        suspend fun ExtensionAwareBuilder.useTransaction(
            provider: KrescentLockProvider,
            duration: Duration,
            vararg keys: String
        ) {
            val lock = when(keys.size) {
                0 -> null
                1 -> provider.getLock(keys.first())
                else -> provider.getMultiLock(keys.toList())
            }
            if (lock == null) error("At least one key must be provided for a transaction lock.")
            registerExtension(ModelLockTransactionHandler(lock, duration))
        }

        /**
         * Establish a transaction boundary for this event model using the [provider] to acquire a lock.
         * The lock will be acquired without a timeout, meaning it will block until the lock is available.
         *
         * Note: You should usually avoid using this method to prevent deadlocks and use a timeout instead.
         */
        suspend fun ExtensionAwareBuilder.useTransaction(
            provider: KrescentLockProvider,
            vararg keys: String
        ) {
            val lock = when(keys.size) {
                0 -> null
                1 -> provider.getLock(keys.first())
                else -> provider.getMultiLock(keys.toList())
            }
            if (lock == null) error("At least one key must be provided for a transaction lock.")
            registerExtension(ModelLockTransactionHandler(lock, null))
        }
    }
}