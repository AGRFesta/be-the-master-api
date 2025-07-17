package org.agrfesta.btm.api.persistence.jdbc.entities

import org.agrfesta.btm.api.controllers.aLanguage
import org.agrfesta.btm.api.model.EmbeddingStatus
import org.agrfesta.btm.api.model.EmbeddingStatus.UNEMBEDDED
import org.agrfesta.btm.api.model.SupportedLanguage
import org.agrfesta.btm.api.services.utils.toNoNanoSec
import org.agrfesta.test.mothers.aRandomUniqueString
import java.time.Instant
import java.util.*

fun aTranslationEntity(
    id: UUID = UUID.randomUUID(),
    chunkId: UUID,
    language: SupportedLanguage = aLanguage(),
    text: String = aRandomUniqueString(),
    embeddingStatus: EmbeddingStatus = UNEMBEDDED,
    createdOn: Instant = Instant.now().toNoNanoSec()
) = TranslationEntity(id, chunkId, language, text, embeddingStatus, createdOn)
