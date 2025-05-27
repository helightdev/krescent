package dev.helight.krescent.synchronization

interface KrescentLockProvider {

    /**
     * Retrieves a lock for the given [identity].
     */
    suspend fun getLock(identity: String): KrescentLock

    /**
     * Retrieves a multi-lock for the given collection of [identities].
     */
    suspend fun getMultiLock(identities: Collection<String>): KrescentLock {
        return KrescentLock.GenericMultiLock(identities.map {
            getLock(it)
        })
    }

    object Extensions {
        /**
         * Retrieves a multi-lock for the given [identities].
         */
        suspend fun KrescentLockProvider.getMultiLock(vararg identities: String): KrescentLock {
            return getMultiLock(identities.asList())
        }
    }
}


