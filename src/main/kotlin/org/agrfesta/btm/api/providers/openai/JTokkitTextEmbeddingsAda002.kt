package org.agrfesta.btm.api.providers.openai

import arrow.core.Either
import arrow.core.right
import com.knuddels.jtokkit.Encodings.newDefaultEncodingRegistry
import com.knuddels.jtokkit.api.Encoding
import com.knuddels.jtokkit.api.EncodingRegistry
import com.knuddels.jtokkit.api.ModelType.TEXT_EMBEDDING_ADA_002
import org.agrfesta.btm.api.model.TokenCountFailure
import org.agrfesta.btm.api.services.Tokenizer
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(name = ["embeddings.provider"], havingValue = "openai")
class JTokkitTextEmbeddingsAda002: Tokenizer {
    private val modelType = TEXT_EMBEDDING_ADA_002
    private val registry: EncodingRegistry = newDefaultEncodingRegistry()
    private val enc: Encoding = registry.getEncodingForModel(modelType)

    override suspend fun countTokens(text: String, isAQuery: Boolean): Either<TokenCountFailure, Int> =
        enc.countTokens(text).right()
}
