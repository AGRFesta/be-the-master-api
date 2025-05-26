package org.agrfesta.btm.api.persistence.jdbc.entities

import org.agrfesta.btm.api.model.EmbeddingStatus
import java.time.Instant
import java.util.*

class TranslationEntity(
    val id: UUID,
    val textBitId: UUID,
    val languageCode: String,
    val text: String,
    val embeddingStatus: EmbeddingStatus,
    val createdOn: Instant
)
