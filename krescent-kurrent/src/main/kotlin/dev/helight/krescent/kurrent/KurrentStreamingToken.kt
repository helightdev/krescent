package dev.helight.krescent.kurrent

import dev.helight.krescent.source.StreamingToken
import io.kurrent.dbclient.ReadStreamOptions
import io.kurrent.dbclient.SubscribeToStreamOptions

sealed class KurrentStreamingToken() : StreamingToken<KurrentStreamingToken> {

    override fun serialize(): String {
        // Doesn't follow the kurrentdb token format, since our interpretation of head and tail is different
        return when (this) {
            is HeadStreamingToken -> "HEAD"
            is TailStreamingToken -> "TAIL"
            is RevisionStreamingToken -> revision.toString()
        }
    }

    override fun compareTo(other: KurrentStreamingToken): Int {
        return when (this) {
            is HeadStreamingToken -> when (other) {
                is HeadStreamingToken -> 0
                is TailStreamingToken -> -1
                is RevisionStreamingToken -> -1
            }

            is TailStreamingToken -> when (other) {
                is HeadStreamingToken -> 1
                is TailStreamingToken -> 0
                is RevisionStreamingToken -> 1
            }

            is RevisionStreamingToken -> when (other) {
                is HeadStreamingToken -> 1
                is TailStreamingToken -> -1
                is RevisionStreamingToken -> revision.compareTo(other.revision)
            }
        }
    }

    abstract fun applyToReadOption(readStreamOptions: ReadStreamOptions): ReadStreamOptions
    abstract fun applyToSubscribeOption(subscribeToStreamOptions: SubscribeToStreamOptions): SubscribeToStreamOptions

    class HeadStreamingToken : KurrentStreamingToken() {
        override fun applyToReadOption(readStreamOptions: ReadStreamOptions): ReadStreamOptions {
            return readStreamOptions.fromStart()
        }

        override fun applyToSubscribeOption(subscribeToStreamOptions: SubscribeToStreamOptions): SubscribeToStreamOptions {
            return subscribeToStreamOptions.fromStart()
        }
    }

    class TailStreamingToken : KurrentStreamingToken() {
        override fun applyToReadOption(readStreamOptions: ReadStreamOptions): ReadStreamOptions {
            return readStreamOptions.fromEnd()
        }

        override fun applyToSubscribeOption(subscribeToStreamOptions: SubscribeToStreamOptions): SubscribeToStreamOptions {
            return subscribeToStreamOptions.fromEnd()
        }
    }

    data class RevisionStreamingToken(val revision: Long) : KurrentStreamingToken() {
        override fun applyToReadOption(readStreamOptions: ReadStreamOptions): ReadStreamOptions {
            return readStreamOptions.fromRevision(revision)
        }

        override fun applyToSubscribeOption(subscribeToStreamOptions: SubscribeToStreamOptions): SubscribeToStreamOptions {
            return subscribeToStreamOptions.fromRevision(revision)
        }

    }
}