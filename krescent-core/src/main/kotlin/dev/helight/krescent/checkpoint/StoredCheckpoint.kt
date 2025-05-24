package dev.helight.krescent.checkpoint

import dev.helight.krescent.serialization.InstantSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.time.Instant

@Serializable
data class StoredCheckpoint(
    val namespace: String,
    val revision: Int,
    val position: String,
    @Serializable(with = InstantSerializer::class)
    val timestamp: Instant,
    val data: JsonElement,
)