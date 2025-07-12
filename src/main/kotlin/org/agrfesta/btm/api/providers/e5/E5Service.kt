package org.agrfesta.btm.api.providers.e5

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import kotlinx.coroutines.runBlocking
import org.agrfesta.btm.api.model.Embedding
import org.agrfesta.btm.api.model.EmbeddingCreationFailure
import org.agrfesta.btm.api.model.TokenCountFailure
import org.agrfesta.btm.api.services.EmbeddingsProvider
import org.agrfesta.btm.api.services.Tokenizer
import org.agrfesta.btm.api.services.utils.LoggerDelegate
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(name = ["embeddings.provider"], havingValue = "e5")
class E5Service(
    private val client: E5Client
): EmbeddingsProvider, Tokenizer {
    private val logger by LoggerDelegate()

    override val name: String
        get() = TODO("Not yet implemented")

    override fun countTokens(text: String): Either<TokenCountFailure, Int> = runBlocking {
            client.countTokens(E5CountTokenRequest(listOf(text)))
                .tokenCounts
                .first().tokenCount.right()
        }

    override suspend fun createEmbedding(text: String): Either<EmbeddingCreationFailure, Embedding> =
        try {
            client.embed(E5EmbedRequest(listOf(text))).vectors.first().right()
        } catch (e: Exception) {
            logger.error("Embed failure!", e)
            EmbeddingCreationFailure.left()
        }

}
