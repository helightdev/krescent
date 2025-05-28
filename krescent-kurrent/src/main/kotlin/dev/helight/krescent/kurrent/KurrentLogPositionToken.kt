package dev.helight.krescent.kurrent

import dev.helight.krescent.source.StreamingToken
import io.kurrent.dbclient.Position
import io.kurrent.dbclient.SubscribeToAllOptions

sealed class KurrentLogPositionToken : StreamingToken<KurrentLogPositionToken> {

    fun applyTo(subscribeToAllOptions: SubscribeToAllOptions): SubscribeToAllOptions = when (this) {
        is PositionToken -> subscribeToAllOptions.fromPosition(position)
        is TailToken -> subscribeToAllOptions.fromEnd()
        is HeadToken -> subscribeToAllOptions.fromStart()
    }

    override fun serialize(): String = when (this) {
        is PositionToken -> "${position.commitUnsigned}/${position.prepareUnsigned}"
        is TailToken -> "TAIL"
        is HeadToken -> "HEAD"
    }

    override fun compareTo(other: KurrentLogPositionToken): Int {
        return when (this) {
            is PositionToken -> when (other) {
                is PositionToken -> position.compareTo(other.position)
                is TailToken -> -1
                is HeadToken -> 1
            }

            is TailToken -> when (other) {
                is PositionToken -> 1
                is TailToken -> 0
                is HeadToken -> 1
            }

            is HeadToken -> when (other) {
                is PositionToken -> -1
                is TailToken -> -1
                is HeadToken -> 0
            }
        }
    }

    class TailToken : KurrentLogPositionToken()
    class HeadToken : KurrentLogPositionToken()
    data class PositionToken(val position: Position) : KurrentLogPositionToken()

    companion object {
        fun decodePosition(encoded: String): KurrentLogPositionToken {
            val split = encoded.split('/')
            if (split.size != 2) {
                throw IllegalArgumentException("Invalid position format: $encoded")
            }
            val position = Position(
                split[0].toLong(),
                split[1].toLong()
            )
            return PositionToken(position)
        }
    }
}