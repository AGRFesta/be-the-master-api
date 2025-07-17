package org.agrfesta.btm.api.persistence.jdbc.entities

import org.agrfesta.btm.api.model.EmbeddingStatus
import org.agrfesta.btm.api.model.SupportedLanguage
import java.time.Instant
import java.util.*

class TranslationEntity(
    val id: UUID,
    val chunkId: UUID,
    val language: SupportedLanguage,
    val text: String,
    val embeddingStatus: EmbeddingStatus,
    val createdOn: Instant
)
