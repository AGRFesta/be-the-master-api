package org.agrfesta.btm.api.providers.openai

import com.knuddels.jtokkit.Encodings.newDefaultEncodingRegistry
import com.knuddels.jtokkit.api.Encoding
import com.knuddels.jtokkit.api.EncodingRegistry
import com.knuddels.jtokkit.api.ModelType.TEXT_EMBEDDING_ADA_002
import org.agrfesta.btm.api.services.Tokenizer
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(name = ["embeddings.provider"], havingValue = "openai")
class JTokkitTextEmbeddingsAda002: Tokenizer {
    private val modelType = TEXT_EMBEDDING_ADA_002
    private val registry: EncodingRegistry = newDefaultEncodingRegistry()
    private val enc: Encoding = registry.getEncodingForModel(modelType)

    override val name: String = modelType.name

    override fun countTokens(text: String): Int = enc.countTokens(text)
}
