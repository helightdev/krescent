package dev.helight.krescent.synchronization

interface KrescentLockProvider {

    suspend fun getLock(identity: String): KrescentLock
    suspend fun getMultiLock(identities: Collection<String>): KrescentLock {
        return KrescentLock.GenericMultiLock(identities.map {
            getLock(it)
        })
    }


    object Extensions {
        suspend fun KrescentLockProvider.getMultiLock(vararg identities: String): KrescentLock {
            return getMultiLock(identities.asList())
        }
    }
}


