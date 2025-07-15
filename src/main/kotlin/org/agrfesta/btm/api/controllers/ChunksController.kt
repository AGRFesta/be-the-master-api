package org.agrfesta.btm.api.controllers

import arrow.core.Either.Left
import arrow.core.Either.Right
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import kotlinx.coroutines.runBlocking
import org.agrfesta.btm.api.controllers.config.MessageResponse
import org.agrfesta.btm.api.controllers.config.NonBlankStringSetDeserializer
import org.agrfesta.btm.api.controllers.config.toResponseEntity
import org.agrfesta.btm.api.model.Embedding
import org.agrfesta.btm.api.model.EmbeddingCreationFailure
import org.agrfesta.btm.api.model.Game
import org.agrfesta.btm.api.model.MissingChunk
import org.agrfesta.btm.api.model.PersistenceFailure
import org.agrfesta.btm.api.model.Topic
import org.agrfesta.btm.api.services.ChunksService
import org.agrfesta.btm.api.services.Embedder
import org.agrfesta.btm.api.services.EmbeddingsProvider
import org.springframework.http.ResponseEntity
import org.springframework.http.ResponseEntity.internalServerError
import org.springframework.http.ResponseEntity.ok
import org.springframework.http.ResponseEntity.status
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.*
import kotlin.math.sqrt

/**
 * REST controller for managing Text Chunks (small text units), their translations,
 * and similarity-based search.
 */
@RestController
@RequestMapping("/chunks")
class ChunksController(
    private val chunksService: ChunksService,
    private val embeddingsProvider: EmbeddingsProvider
) {
    private val embedder: Embedder = {text -> runBlocking { embeddingsProvider.createEmbedding(text) }}

    /**
     * POST /chunks
     *
     * Creates multiple chunks for a specific game and topic, each associated with a translation
     * in the given language. Optionally, each translation can be embedded for similarity search.
     *
     * @param request the [ChunksCreationRequest] containing game, topic, language, list of texts, and embed flag.
     * @return 200 OK with success message if all chunks are created,
     *         400 Bad Request if the input validation fails,
     *         500 Internal Server Error if persistence fails during chunk creation or translation.
     */
    @PostMapping
    fun createChunks(@Valid @RequestBody request: ChunksCreationRequest): ResponseEntity<Any> {
        request.texts.forEach {
            when (val insertResult = chunksService.createChunk(request.game, request.topic)) {
                is Left -> {/* for the moment ignores it, but we should implement a retry queue */}
                is Right -> {
                    val chunkId = insertResult.value
                    chunksService.replaceTranslation(chunkId, request.language, it,
                        if (request.embed != false) embedder else null)
                }
            }
        }
        return ok().body(MessageResponse("${request.texts.size} Chunks successfully persisted!"))
    }

    /**
     * PATCH /chunks/{id}
     *
     * Updates or replaces the translation of an existing chunk, for a given language.
     * Optionally, the translation is embedded if not done in batch.
     *
     * @param id the UUID of the chunk to update
     * @param request the [ChunkTranslationPatchRequest] containing new text, language, and batch flag
     * @return 200 OK if updated (with optional embedding warning),
     *         400 Bad Request if the text is empty,
     *         404 Not Found if the chunk doesn't exist,
     *         500 Internal Server Error if persistence or embedding fails.
     */
    @PatchMapping("/{id}")
    fun update(@PathVariable id: UUID, @Valid @RequestBody request: ChunkTranslationPatchRequest): ResponseEntity<Any> {
        return chunksService.findChunk(id).flatMap {
            if (it == null) MissingChunk.left()
            else chunksService.replaceTranslation(
                it.id,
                request.language,
                request.text,
                if (request.inBatch) null else embedder
            )
        }.fold(
            ifLeft = {
                when(it) {
                    EmbeddingCreationFailure -> ok()
                        .body(MessageResponse("Chunk $id successfully patched! But embedding creation failed!"))
                    is PersistenceFailure -> internalServerError()
                        .body(MessageResponse("Unable to replace chunk $id!"))
                    MissingChunk -> status(404).body(MessageResponse("Chunk $id is missing!"))
                }
            },
            ifRight = { ok().body(MessageResponse("Chunk $id successfully patched!")) }
        )
    }

    /**
     * POST /chunks/similarity-search
     *
     * Performs a similarity search based on the embedding of the provided text.
     * It compares the embedding to existing chunk embeddings filtered by game, topic, and language.
     *
     * @param request the [ChunkSearchBySimilarityRequest] containing the text to search,
     *                target game, topic, language, and optional search parameters.
     * @return 200 OK with list of similar chunks sorted by distance,
     *         400 Bad Request if input is invalid,
     *         500 Internal Server Error on processing failure.
     */
    @PostMapping("/similarity-search")
    fun similaritySearch(@Valid @RequestBody request: ChunkSearchBySimilarityRequest): ResponseEntity<Any> {
            return chunksService.searchBySimilarity(
                request.text,
                request.game,
                request.topic,
                request.language,
                embedder,
                request.embeddingsLimit,
                request.distanceLimit
            ).flatMap { result -> result.map { it.toSimilarityResultItem() }.right() }
                .toResponseEntity()
        }

}

data class ChunksCreationRequest(

    val game: Game,

    val topic: Topic,

    @field:NotBlank(message = "language must not be blank and two charters long!")
    @field:Size(min = 2, max = 2, message = "language must not be blank and two charters long!")
    val language: String,

    @field:JsonDeserialize(using = NonBlankStringSetDeserializer::class)
    @field:NotEmpty(message = "No chunks to create!")
    val texts: Set<String>,

    val embed: Boolean?

)

data class ChunkTranslationPatchRequest(

    @field:NotBlank(message = "text must not be blank!")
    val text: String,

    @field:NotBlank(message = "language must not be blank and two charters long!")
    @field:Size(min = 2, max = 2, message = "language must not be blank and two charters long!")
    val language: String,

    val inBatch: Boolean = false
)

data class ChunkSearchBySimilarityRequest(

    val game: Game,

    val topic: Topic,

    @field:NotBlank(message = "text must not be blank!")
    val text: String,

    @field:NotBlank(message = "language must not be blank and two charters long!")
    @field:Size(min = 2, max = 2, message = "language must not be blank and two charters long!")
    val language: String,

    @field:Positive(message = "embeddingsLimit must be a positive Int!")
    val embeddingsLimit: Int?,

    @field:DecimalMin(value = "0.0", inclusive = false, message = "distanceLimit must be in (0.0 ; 2.0)!")
    @field:DecimalMax(value = "2.0", inclusive = false, message = "distanceLimit must be in (0.0 ; 2.0)!")
    val distanceLimit: Double?

)

data class SimilarityResultItem(
    val text: String,
    val distance: Double
)

private fun Pair<String, Double>.toSimilarityResultItem() = SimilarityResultItem(first, second)

fun Embedding.normalize(): Embedding {
    val norm = sqrt(map { it * it }.sum())
    return if (norm == 0f) this else map { it / norm }.toFloatArray()
}
