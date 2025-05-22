package org.agrfesta.btm.api.persistence.jdbc.entities

import org.agrfesta.btm.api.model.Embedding
import java.time.Instant
import java.util.*

class EmbeddingEntity(
    val id: UUID,
    val translationId: UUID,
    val vector: Embedding,
    val createdOn: Instant
)
