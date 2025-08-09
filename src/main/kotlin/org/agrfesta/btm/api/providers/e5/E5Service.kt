package org.agrfesta.btm.api.providers.e5

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.agrfesta.btm.api.model.Embedding
import org.agrfesta.btm.api.model.EmbeddingCreationFailure
import org.agrfesta.btm.api.model.TokenCountFailure
import org.agrfesta.btm.api.providers.e5.E5EmbedMode.PASSAGE
import org.agrfesta.btm.api.providers.e5.E5EmbedMode.QUERY
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

    override suspend fun countTokens(text: String, isAQuery: Boolean): Either<TokenCountFailure, Int> =
        try {
            client.countTokens(E5CountTokenRequest(listOf(text), if (isAQuery) QUERY else PASSAGE))
                .tokenCounts
                .first().tokenCount.right()
        } catch (e: Exception) {
            logger.error("Token count failure!", e)
            TokenCountFailure("Token count failure").left()
        }

    override suspend fun createEmbedding(text: String, isAQuery: Boolean): Either<EmbeddingCreationFailure, Embedding> =
        try {
            val mode = if (isAQuery) QUERY else PASSAGE
            logger.info("Creating E5 model embedding, mode $mode")
            client.embed(E5EmbedRequest(listOf(text), mode)).vectors.first().right()
        } catch (e: Exception) {
            logger.error("Embed failure!", e)
            EmbeddingCreationFailure("Embed failed").left()
        }

}
