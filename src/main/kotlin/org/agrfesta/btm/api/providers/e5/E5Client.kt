package org.agrfesta.btm.api.providers.e5

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttpConfig
import io.ktor.client.engine.okhttp.OkHttpEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.jackson.jackson
import org.agrfesta.btm.api.model.Embedding
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class E5Client(
    @Value("\${providers.e5.base-url}") private val baseUrl: String,
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
    }

    suspend fun embed(request: E5EmbedRequest): E5EmbedResponse =
        client.post("$baseUrl/embed") {
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(request)
        }
            .body<E5EmbedResponse>()

    suspend fun countTokens(request: E5CountTokenRequest): E5CountTokensResponse =
        client.post("$baseUrl/count-tokens") {
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(request)
        }
            .body<E5CountTokensResponse>()

}

enum class E5EmbedMode {QUERY, PASSAGE}

data class E5EmbedRequest(
    val sentences: List<String>,
    val mode: E5EmbedMode
)

data class E5EmbedResponse(
    val vectors: List<Embedding>
)

data class E5CountTokenRequest(
    val sentences: List<String>,
    val mode: E5EmbedMode
)

data class E5CountTokensResponse(
    @JsonProperty("token_counts") val tokenCounts: List<E5TokenCount>
)

data class E5TokenCount(
    @JsonProperty("sentence") val sentence: String,
    @JsonProperty("token_count") val tokenCount: Int
)
