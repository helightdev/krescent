package dev.helight.krescent.source

/**
 * A token representing a position in the event stream.
 * The implementation can specify what exactly the token encapsulates.
 */
interface StreamingToken<SELF : StreamingToken<SELF>> : Comparable<SELF> {
    /**
     * Serializes the token into a string representation.
     *
     * @return A string representing the serialized token.
     */
    fun serialize(): String
}