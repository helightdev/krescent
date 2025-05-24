package dev.helight.krescent.event

import kotlinx.coroutines.flow.Flow

fun interface VirtualEventIngest {

    suspend fun ingest(event: Event): Flow<VirtualEvent>

}