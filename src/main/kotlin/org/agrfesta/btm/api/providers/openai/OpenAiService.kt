package org.agrfesta.btm.api.providers.openai

import arrow.core.Either
import org.agrfesta.btm.api.model.Embedding
import org.agrfesta.btm.api.model.EmbeddingCreationFailure
import org.agrfesta.btm.api.services.EmbeddingsProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(name = ["embeddings.provider"], havingValue = "openai")
class OpenAiService(
    private val openAiClient: OpenAiClient
): EmbeddingsProvider {

    override suspend fun createEmbedding(text: String): Either<EmbeddingCreationFailure, Embedding> =
        openAiClient.createEmbedding(text)
            .map {
                //TODO print usage
                it.embedding
            }

}
