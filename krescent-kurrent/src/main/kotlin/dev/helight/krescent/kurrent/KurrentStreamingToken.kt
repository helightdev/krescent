package dev.helight.krescent.kurrent

import dev.helight.krescent.source.StreamingToken
import io.kurrent.dbclient.ReadStreamOptions
import io.kurrent.dbclient.SubscribeToStreamOptions

sealed class KurrentStreamingToken() : StreamingToken<KurrentStreamingToken> {

    override fun serialize(): String {
        // Doesn't follow the kurrentdb token format, since our interpretation of head and tail is different
        return when (this) {
            is HeadToken -> "HEAD"
            is TailToken -> "TAIL"
            is RevisionToken -> revision.toString()
        }
    }

    override fun compareTo(other: KurrentStreamingToken): Int {
        return when (this) {
            is HeadToken -> when (other) {
                is HeadToken -> 0
                is TailToken -> -1
                is RevisionToken -> -1
            }

            is TailToken -> when (other) {
                is HeadToken -> 1
                is TailToken -> 0
                is RevisionToken -> 1
            }

            is RevisionToken -> when (other) {
                is HeadToken -> 1
                is TailToken -> -1
                is RevisionToken -> revision.compareTo(other.revision)
            }
        }
    }

    fun applyTo(readStreamOptions: ReadStreamOptions): ReadStreamOptions = when (this) {
        is HeadToken -> readStreamOptions.fromStart()
        is TailToken -> readStreamOptions.fromEnd()
        is RevisionToken -> readStreamOptions.fromRevision(revision)
    }

    fun applyTo(subscribeToStreamOptions: SubscribeToStreamOptions): SubscribeToStreamOptions = when (this) {
        is HeadToken -> subscribeToStreamOptions.fromStart()
        is TailToken -> subscribeToStreamOptions.fromEnd()
        is RevisionToken -> subscribeToStreamOptions.fromRevision(revision)
    }

    class HeadToken : KurrentStreamingToken()
    class TailToken : KurrentStreamingToken()
    data class RevisionToken(val revision: Long) : KurrentStreamingToken()
}

