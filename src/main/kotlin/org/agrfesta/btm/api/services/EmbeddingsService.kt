package org.agrfesta.btm.api.services

import arrow.core.Either
import org.agrfesta.btm.api.model.Embedding
import org.agrfesta.btm.api.model.EmbeddingCreationFailure

interface EmbeddingsService {

    suspend fun createEmbedding(text: String): Either<EmbeddingCreationFailure, Embedding>

}
