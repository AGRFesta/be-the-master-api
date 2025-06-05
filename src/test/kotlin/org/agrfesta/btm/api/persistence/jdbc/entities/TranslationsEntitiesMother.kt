package org.agrfesta.btm.api.persistence.jdbc.entities

import org.agrfesta.btm.api.model.EmbeddingStatus
import org.agrfesta.btm.api.model.EmbeddingStatus.UNEMBEDDED
import org.agrfesta.btm.api.services.utils.toNoNanoSec
import org.agrfesta.test.mothers.aRandomUniqueString
import java.time.Instant
import java.util.*

fun aTranslationEntity(
    id: UUID = UUID.randomUUID(),
    chunkId: UUID,
    languageCode: String = aRandomUniqueString(),
    text: String = aRandomUniqueString(),
    embeddingStatus: EmbeddingStatus = UNEMBEDDED,
    createdOn: Instant = Instant.now().toNoNanoSec()
) = TranslationEntity(id, chunkId, languageCode, text, embeddingStatus, createdOn)
