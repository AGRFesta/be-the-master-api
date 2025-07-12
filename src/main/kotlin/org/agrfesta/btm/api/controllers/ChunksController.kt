package org.agrfesta.btm.api.controllers

import arrow.core.Either.Left
import arrow.core.Either.Right
import arrow.core.flatMap
import arrow.core.right
import kotlinx.coroutines.runBlocking
import org.agrfesta.btm.api.model.BtmConfigurationFailure
import org.agrfesta.btm.api.model.Embedding
import org.agrfesta.btm.api.model.EmbeddingCreationFailure
import org.agrfesta.btm.api.model.Game
import org.agrfesta.btm.api.model.PersistenceFailure
import org.agrfesta.btm.api.model.TokenCountFailure
import org.agrfesta.btm.api.model.Topic
import org.agrfesta.btm.api.model.Translation
import org.agrfesta.btm.api.model.ValidationFailure
import org.agrfesta.btm.api.services.ChunksService
import org.agrfesta.btm.api.services.Embedder
import org.agrfesta.btm.api.services.EmbeddingsProvider
import org.springframework.http.ResponseEntity
import org.springframework.http.ResponseEntity.badRequest
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
    fun createChunks(@RequestBody request: ChunksCreationRequest): ResponseEntity<Any> = request.validate()
        .flatMap { valid ->
            valid.texts.forEach {
                when (val insertResult = chunksService.createChunk(valid.game, valid.topic)) {
                    is Left -> {/* for the moment ignores it, but we should implement a retry queue */}
                    is Right -> {
                        val chunkId = insertResult.value
                        chunksService.replaceTranslation(chunkId, valid.language, it,
                            if (request.embed != false) embedder else null)
                    }
                }
            }
            "${valid.texts.size} Chunks successfully persisted!".right()
        }.fold(
            ifLeft = { badRequest().body(MessageResponse(it.message)) },
            ifRight = { ok().body(MessageResponse(it)) }
        )

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
    fun update(@PathVariable id: UUID, @RequestBody request: ChunkTranslationPatchRequest): ResponseEntity<Any> {
        if (request.text.isBlank()) {
            return badRequest().body(MessageResponse("Text must not be empty!"))
        }
        val chunk = try {
            chunksService.findChunk(id)
                ?: return status(404).body(MessageResponse("Chunk $id is missing!"))
        } catch (e: Exception) {
            return internalServerError()
                .body(MessageResponse("Unable to fetch chunk!"))
        }
        return chunksService.replaceTranslation(
            chunk.id,
            request.language,
            request.text,
            if (request.inBatch) null else embedder
        ).fold(
             ifLeft = {
                 when(it) {
                     EmbeddingCreationFailure -> ok()
                         .body(MessageResponse("Chunk $id successfully patched! But embedding creation failed!"))
                     is PersistenceFailure -> internalServerError()
                         .body(MessageResponse("Unable to replace chunk $id!"))
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
    fun similaritySearch(@RequestBody request: ChunkSearchBySimilarityRequest): ResponseEntity<Any> = request.validate()
        .flatMap { valid ->
            chunksService.searchBySimilarity(
                valid.text,
                valid.game,
                valid.topic,
                valid.language,
                embedder,
                valid.embeddingsLimit,
                valid.distanceLimit
            ).flatMap { result ->
                    result.map { it.toSimilarityResultItem() }.right()
                }
        }.toResponseEntity()

}

data class ChunksCreationRequest(
    val game: String?,
    val topic: String?,
    val language: String?,
    val texts: List<String>?,
    val embed: Boolean?
)

data class ChunkTranslationPatchRequest(
    val text: String,
    val language: String,
    val inBatch: Boolean = false
)

data class ChunkSearchBySimilarityRequest(
    val game: String,
    val topic: String,
    val text: String,
    val language: String,
    val embeddingsLimit: Int?,
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
