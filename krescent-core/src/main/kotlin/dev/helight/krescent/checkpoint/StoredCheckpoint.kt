package dev.helight.krescent.checkpoint

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class StoredCheckpoint(
    val namespace: String,
    val version: String,
    val position: String,
    val timestamp: Instant,
    val data: CheckpointBucket,
)