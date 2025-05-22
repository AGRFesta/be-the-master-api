package org.agrfesta.btm.api.services

import arrow.core.Either
import org.agrfesta.btm.api.model.Embedding
import org.agrfesta.btm.api.model.EmbeddingCreationFailure

typealias Embedder = (String) -> Either<EmbeddingCreationFailure, Embedding>

interface EmbeddingsProvider {

    suspend fun createEmbedding(text: String): Either<EmbeddingCreationFailure, Embedding>

}
