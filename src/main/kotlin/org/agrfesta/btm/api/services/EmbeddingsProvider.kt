package org.agrfesta.btm.api.services

import arrow.core.Either
import org.agrfesta.btm.api.model.Embedding
import org.agrfesta.btm.api.model.EmbeddingCreationFailure

/**
 * Functional alias for an embedding generator.
 *
 * Represents a function that takes a [String] as input (typically a sentence or prompt)
 * and returns an [Either] containing an [Embedding] on success or an [EmbeddingCreationFailure] on failure.
 *
 * This alias allows embedding logic to be injected as a lambda or passed around as a dependency
 * in services and controllers.
 */
typealias Embedder = (String) -> Either<EmbeddingCreationFailure, Embedding>

/**
 * Abstraction for embedding generation services.
 *
 * Implementations of this interface are responsible for producing dense vector representations
 * (embeddings) from raw text, typically using an external model or service like Sentence Transformers,
 * Hugging Face models, or an internal model wrapper.
 */
interface EmbeddingsProvider {

    /**
     * Generates an [Embedding] from the given input [text].
     *
     * @param text the input text to embed.
     * @return an [Either] containing the [Embedding] on success,
     *         or an [EmbeddingCreationFailure] if embedding fails (e.g., model error, invalid input, etc.).
     */
    suspend fun createEmbedding(text: String): Either<EmbeddingCreationFailure, Embedding>

}
