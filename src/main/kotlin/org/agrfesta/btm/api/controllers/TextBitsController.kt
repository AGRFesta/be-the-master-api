package org.agrfesta.btm.api.controllers

import arrow.core.Either.Left
import arrow.core.Either.Right
import kotlinx.coroutines.runBlocking
import org.agrfesta.btm.api.model.Embedding
import org.agrfesta.btm.api.model.EmbeddingCreationFailure
import org.agrfesta.btm.api.model.Game
import org.agrfesta.btm.api.model.PersistenceFailure
import org.agrfesta.btm.api.model.TextBit
import org.agrfesta.btm.api.model.Topic
import org.agrfesta.btm.api.model.Translation
import org.agrfesta.btm.api.services.Embedder
import org.agrfesta.btm.api.services.EmbeddingsProvider
import org.agrfesta.btm.api.services.TextBitsService
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
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
@RequestMapping("/text-bits")
class TextBitsController(
    private val textBitsService: TextBitsService,
    private val embeddingsProvider: EmbeddingsProvider
) {
    private val embedder: Embedder = {text -> runBlocking { embeddingsProvider.createEmbedding(text) }}

    /**
     * POST /text-bits
     *
     * Creates a new text bit with a translation for a given game and topic.
     *
     * @param request the [TextBitCreationRequest] containing game, topic, translation, and batch flag.
     * @return 200 OK if successful (with optional embedding warning), 400 if input is invalid,
     *         or 500 if persistence fails.
     */
    @PostMapping
    fun createTextBit(@RequestBody request: TextBitCreationRequest): ResponseEntity<Any> {
        if (request.translation.text.isBlank()) {
            return badRequest().body(MessageResponse("Text must not be empty!"))
        }
        val textBit = try {
            request.toTextBit()
        } catch (e: IllegalArgumentException) {
            return badRequest().body(MessageResponse(e.message ?: "no message"))
        }

        when (val insertResult = textBitsService.createTextBit(request.game, request.topic)) {
            is Left -> return status(INTERNAL_SERVER_ERROR).body(MessageResponse("Unable to create text bit!"))
            is Right -> {
                val textBitId = insertResult.value
                val languageFailures = mutableSetOf<String>()
                request.translation.persist(textBitId, request.inBatch, languageFailures)
//                textBit.translations.forEach {
//                    it.persist(textBitId, request.inBatch, languageFailures)
//                }
                val warning = if (languageFailures.isEmpty()) ""
                else " Failed embeddings creation."
                return ok().body(MessageResponse("Text bit successfully persisted!$warning"))
            }
        }
    }

    /**
     * PATCH /text-bits/{id}
     *
     * Replaces an existing translation of a Text Bit with new text content.
     *
     * @param id the UUID of the text bit to update
     * @param request the [TextBitTranslationPatchRequest] containing updated text, language, and batch flag
     * @return 200 OK if successful, possibly with embedding failure warning,
     *         400 if input is invalid, 404 if bit not found, or 500 on error
     */
    @PatchMapping("/{id}")
    fun update(@PathVariable id: UUID, @RequestBody request: TextBitTranslationPatchRequest): ResponseEntity<Any> {
        if (request.text.isBlank()) {
            return badRequest().body(MessageResponse("Text must not be empty!"))
        }
        val textBit = try {
            textBitsService.findTextBit(id)
                ?: return status(404).body(MessageResponse("Text bit $id is missing!"))
        } catch (e: Exception) {
            return internalServerError()
                .body(MessageResponse("Unable to fetch text bit!"))
        }

        return when(val result = textBitsService.replaceTranslation(
            textBit.id,
            request.language,
            request.text,
            if (request.inBatch) null else embedder)
        ) {
            is Left -> when(result.value) {
                EmbeddingCreationFailure -> ok()
                    .body(MessageResponse("Text bit $id successfully patched! But embedding creation failed!"))
                is PersistenceFailure -> internalServerError()
                    .body(MessageResponse("Unable to replace text bit $id!"))
            }
            is Right -> ok().body(MessageResponse("Text bit $id successfully patched!"))
        }
    }

    /**
     * POST /text-bits/similarity-search
     *
     * Performs similarity search based on the embedding of the provided text.
     *
     * @param request the [TextBitSearchBySimilarityRequest] including game, topic, text, and language.
     * @return 200 OK with list of similar Text Bits, or appropriate error responses.
     */
    @PostMapping("/similarity-search")
    fun similaritySearch(@RequestBody request: TextBitSearchBySimilarityRequest): ResponseEntity<Any> {
        if (request.text.isBlank()) {
            return badRequest().body(MessageResponse("Text must not be blank!"))
        }
        if (request.language.isBlank() || request.language.length != 2) {
            return badRequest().body(MessageResponse("Language must not be blank and two charters length!"))
        }
        val game = try {
            Game.valueOf(request.game)
        } catch (e: IllegalArgumentException) {
            return badRequest().body(MessageResponse("Game is not valid!"))
        }
        val topic = try {
            Topic.valueOf(request.topic)
        } catch (e: IllegalArgumentException) {
            return badRequest().body(MessageResponse("Topic is not valid!"))
        }
       return when(val result = textBitsService
           .searchBySimilarity(request.text, game, topic, request.language, embedder)) {
                is Left -> when(result.value) {
                    EmbeddingCreationFailure -> internalServerError()
                        .body(MessageResponse("Unable to create target embedding!"))
                    is PersistenceFailure -> internalServerError()
                        .body(MessageResponse("Unable to fetch embeddings!"))
                }
                is Right -> ok().body(result.value)
            }
    }

    private fun Translation.persist(
        textBitId: UUID,
        inBatch: Boolean,
        languageFailures: MutableSet<String>
    ) {
        textBitsService.persistTranslation(
            this,
            textBitId,
            if (inBatch) null else embedder
        ).onLeft {
            languageFailures.add(language)
        }
    }

}

data class TextBitCreationRequest(
    val game: Game,
    val topic: Topic,
    val translation: Translation,
    val inBatch: Boolean = false
) {
    fun toTextBit() = TextBit(
        id = UUID.randomUUID(),
        game = game,
        topic = topic,
        translations = setOf(translation)
    )
}

data class TextBitTranslationPatchRequest(
    val text: String,
    val language: String,
    val inBatch: Boolean = false
)

data class TextBitSearchBySimilarityRequest(
    val game: String,
    val topic: String,
    val text: String,
    val language: String
)

fun Embedding.normalize(): Embedding {
    val norm = sqrt(map { it * it }.sum())
    return if (norm == 0f) this else map { it / norm }.toFloatArray()
}
