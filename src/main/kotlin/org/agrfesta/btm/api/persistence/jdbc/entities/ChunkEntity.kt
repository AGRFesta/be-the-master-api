package org.agrfesta.btm.api.persistence.jdbc.entities

import java.time.Instant
import java.util.*
import org.agrfesta.btm.api.model.Topic

class ChunkEntity(
    val id: UUID,
    val gameId: UUID,
    val topic: Topic,
    val createdOn: Instant,
    val updatedOn: Instant?
)
