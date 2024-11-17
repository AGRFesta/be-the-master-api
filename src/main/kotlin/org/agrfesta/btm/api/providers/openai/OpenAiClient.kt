package org.agrfesta.btm.api.providers.openai

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttpConfig
import io.ktor.client.engine.okhttp.OkHttpEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.contentType
import io.ktor.serialization.jackson.jackson
import org.agrfesta.btm.api.model.Embedding
import org.agrfesta.btm.api.model.EmbeddingCreationFailure
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class OpenAiClient(
    private val config: OpenAiConfiguration,
    private val objectMapper: ObjectMapper,
    @Autowired(required = false) engine: HttpClientEngine = OkHttpEngine(OkHttpConfig())
) {
    private val client = HttpClient(engine) {
        expectSuccess = true
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
        }
        install(ContentNegotiation) {
            jackson { }
        }
        install(Auth) {
            bearer {
                loadTokens {
                    BearerTokens(config.apiKey, config.apiKey)
                }
            }
        }
    }

    suspend fun createEmbedding(text: String): Either<EmbeddingCreationFailure, OpenAiEmbeddingCreationResult> {
        val content = client.post("${config.baseUrl}/embeddings") {
            contentType(Json)
            setBody(
                CreateEmbeddingRequest(
                    model = "text-embedding-ada-002",
                    input = text
                )
            )
        }.bodyAsText()
        val jsonNode = objectMapper.readTree(content)
        val usageNode = jsonNode.at("/usage")
        val openAiUsage: OpenAiUsage = objectMapper.treeToValue(usageNode, OpenAiUsage::class.java)
        val embeddingNode = jsonNode.at("/data/0/embedding")
        return if (embeddingNode.isArray) {
            OpenAiEmbeddingCreationResult(
                embedding = embeddingNode.map { it.floatValue() }.toFloatArray(),
                usage = openAiUsage
            ).right()
        } else {
            EmbeddingCreationFailure.left()
        }
    }

}

class OpenAiEmbeddingCreationResult(
    val embedding: Embedding,
    val usage: OpenAiUsage
)

data class OpenAiUsage(
    @JsonProperty("prompt_tokens") val promptToken: Int,
    @JsonProperty("total_tokens") val totalTokens: Int
)

data class CreateEmbeddingRequest(
    val model: String,
    val input: String,
    val user: String? = null
)
