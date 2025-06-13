package org.agrfesta.btm.api.controllers

import arrow.core.Either.Left
import arrow.core.Either.Right
import arrow.core.flatMap
import arrow.core.right
import kotlinx.coroutines.runBlocking
import org.agrfesta.btm.api.model.Embedding
import org.agrfesta.btm.api.model.EmbeddingCreationFailure
import org.agrfesta.btm.api.model.Game
import org.agrfesta.btm.api.model.PersistenceFailure
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
 * REST controller for managing Text Bits (small text units), their translations,
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
     * Creates a new text bit with a translation for a given game and topic.
     *
     * @param request the [ChunkCreationRequest] containing game, topic, translation, and batch flag.
     * @return 200 OK if successful (with optional embedding warning), 400 if input is invalid,
     *         or 500 if persistence fails.
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
     * Replaces an existing translation of a Text Bit with new text content.
     *
     * @param id the UUID of the text bit to update
     * @param request the [ChunkTranslationPatchRequest] containing updated text, language, and batch flag
     * @return 200 OK if successful, possibly with embedding failure warning,
     *         400 if input is invalid, 404 if bit not found, or 500 on error
     */
    @PatchMapping("/{id}")
    fun update(@PathVariable id: UUID, @RequestBody request: ChunkTranslationPatchRequest): ResponseEntity<Any> {
        if (request.text.isBlank()) {
            return badRequest().body(MessageResponse("Text must not be empty!"))
        }
        val chunk = try {
            chunksService.findChunk(id)
                ?: return status(404).body(MessageResponse("Text bit $id is missing!"))
        } catch (e: Exception) {
            return internalServerError()
                .body(MessageResponse("Unable to fetch text bit!"))
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
                         .body(MessageResponse("Text bit $id successfully patched! But embedding creation failed!"))
                     is PersistenceFailure -> internalServerError()
                         .body(MessageResponse("Unable to replace text bit $id!"))
                     is ValidationFailure -> TODO()
                 }
             },
             ifRight = { ok().body(MessageResponse("Text bit $id successfully patched!")) }
         )
    }

    /**
     * POST /chunks/similarity-search
     *
     * Performs similarity search based on the embedding of the provided text.
     *
     * @param request the [ChunkSearchBySimilarityRequest] including game, topic, text, and language.
     * @return 200 OK with list of similar Text Bits, or appropriate error responses.
     */
    @PostMapping("/similarity-search")
    fun similaritySearch(@RequestBody request: ChunkSearchBySimilarityRequest): ResponseEntity<Any> = request.validate()
        .flatMap { valid ->
            chunksService.searchBySimilarity(
                valid.text, valid.game, valid.topic, valid.language, embedder).flatMap { result ->
                    result.map { it.toSimilarityResultItem() }.right()
                }
        }.fold(
            ifLeft = {
                when(it) {
                    EmbeddingCreationFailure -> internalServerError()
                        .body(MessageResponse("Unable to create target embedding!"))
                    is PersistenceFailure -> internalServerError()
                        .body(MessageResponse("Unable to fetch embeddings!"))
                    is ValidationFailure -> badRequest().body(MessageResponse(it.message))
                }
            },
            ifRight = { ok().body(it) }
        )

}

data class ChunksCreationRequest(
    val game: String?,
    val topic: String?,
    val language: String?,
    val texts: List<String>?,
    val embed: Boolean?
)

data class ChunkCreationRequest(
    val game: Game,
    val topic: Topic,
    val translation: Translation,
    val inBatch: Boolean = false
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
    val language: String
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
