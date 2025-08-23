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
import java.util.*
import kotlinx.coroutines.runBlocking
import org.agrfesta.btm.api.controllers.config.MessageResponse
import org.agrfesta.btm.api.controllers.config.NonBlankChunkContentSetDeserializer
import org.agrfesta.btm.api.controllers.config.toResponseEntity
import org.agrfesta.btm.api.model.EmbeddingCreationFailure
import org.agrfesta.btm.api.model.MissingChunk
import org.agrfesta.btm.api.model.PersistenceFailure
import org.agrfesta.btm.api.model.SupportedLanguage
import org.agrfesta.btm.api.model.Topic
import org.agrfesta.btm.api.persistence.GamesDao
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

/**
 * REST controller for managing Text Chunks (small text units), their translations,
 * and similarity-based search.
 * We assume that all chunks are not queries.
 */
@RestController
@RequestMapping("/chunks")
class ChunksController(
    private val chunksService: ChunksService,
    private val gamesDao: GamesDao,
    private val embeddingsProvider: EmbeddingsProvider
) {
    private val embedder: Embedder = {text -> runBlocking { embeddingsProvider.createEmbedding(text, false) }}

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
        val game = gamesDao.findGameByName(request.game)
            ?: return status(404).body(MessageResponse("game ${request.game} is missing!"))
        request.chunksContent
            .map { it.text }
            .forEach {
                when (val insertResult = chunksService.createChunk(game, request.topic)) {
                    is Left -> {/* for the moment ignores it, but we should implement a retry queue */}
                    is Right -> {
                        val chunkId = insertResult.value
                        chunksService.replaceTranslation(chunkId, request.language, it,
                            if (request.embed != false) embedder else null)
                    }
                }
            }
        return ok().body(MessageResponse("${request.chunksContent.size} Chunks successfully persisted!"))
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
                    is EmbeddingCreationFailure -> ok()
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
     * @return 200 OK with list of similar chunks sorted by distance.
     *         400 Bad Request if input is invalid.
     *         404 Not Found if game is not found.
     *         500 Internal Server Error on processing failure.
     */
    @PostMapping("/similarity-search")
    fun similaritySearch(@Valid @RequestBody request: ChunkSearchBySimilarityRequest): ResponseEntity<Any> {
            val game = gamesDao.findGameByName(request.game)
                ?: return status(404).body(MessageResponse("game ${request.game} is missing!"))
            return chunksService.searchBySimilarity(
                request.text,
                game,
                request.topic,
                request.language,
                embedder,
                request.embeddingsLimit,
                request.distanceLimit
            ).flatMap { result -> result.map { it.toSimilarityResultItem() }.right() }
                .toResponseEntity()
        }

}

/**
 * Data class representing the content of a chunk.
 *
 * @property text The main content of the chunk.
 * @property nonSemanticaPart Optional additional part of the content without semantic meaning.
 */
data class ChunkContent(
    val text: String,
    val nonSemanticaPart: String? = null
)

/** Request body for `POST /chunks` (bulk creation). */
data class ChunksCreationRequest(
    /** Case‑sensitive unique game name. Must exist. */
    val game: String,

    /** The domain topic that groups chunks within a game. */
    val topic: Topic,

    /** Language code of the initial translation (e.g., `EN`, `IT`). */
    val language: SupportedLanguage,

    /**
     * Set of non‑blank texts to create as individual chunks.
     * A custom deserializer trims values and rejects blank/whitespace entries.
     */
    @field:JsonDeserialize(using = NonBlankChunkContentSetDeserializer::class)
    @field:NotEmpty(message = "No chunks to create!")
    val chunksContent: Set<ChunkContent>,

    /** If `true` (default when omitted), compute an embedding for each translation. */
    val embed: Boolean?
)

/** Request body for `PATCH /chunks/{id}` (translation upsert/replace). */
data class ChunkTranslationPatchRequest(
    /** New translation text (non‑blank). */
    @field:NotBlank(message = "text must not be blank!")
    val text: String,

    /** Language for the translation to update/replace. */
    val language: SupportedLanguage,

    /** If `true`, skip embedding (assume a later batch job will handle it). */
    val inBatch: Boolean = false
)

/** Request body for `POST /chunks/similarity-search`. */
data class ChunkSearchBySimilarityRequest(
    /** Case‑sensitive game name. Must exist. */
    val game: String,

    /** Topic scope to restrict candidate translations. */
    val topic: Topic,

    /** Query text to embed and compare (non‑blank). */
    @field:NotBlank(message = "text must not be blank!")
    val text: String,

    /** Language scope to restrict candidate translations. */
    val language: SupportedLanguage,

    /** Optional DB limit (> 0) for the neighbor search. */
    @field:Positive(message = "embeddingsLimit must be a positive Int!")
    val embeddingsLimit: Int?,

    /**
     * Optional maximum cosine distance (0.0, 2.0) for early filtering.
     * Values are exclusive on both ends per validation constraints.
     */
    @field:DecimalMin(value = "0.0", inclusive = false, message = "distanceLimit must be in (0.0 ; 2.0)!")
    @field:DecimalMax(value = "2.0", inclusive = false, message = "distanceLimit must be in (0.0 ; 2.0)!")
    val distanceLimit: Double?
)

/** Single search hit returned by similarity search. */
data class SimilarityResultItem(
    /** The matched translation text. */
    val text: String,
    /** Cosine distance to the query embedding (lower is more similar). */
    val distance: Double
)

/** Maps a persistence layer result pair to the API response model. */
private fun Pair<String, Double>.toSimilarityResultItem() = SimilarityResultItem(first, second)
