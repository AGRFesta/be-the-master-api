package org.agrfesta.btm.api.controllers

import arrow.core.Either.Left
import arrow.core.Either.Right
import kotlinx.coroutines.runBlocking
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

@RestController
@RequestMapping("/text-bits")
class TextBitsController(
    private val textBitsService: TextBitsService,
    private val embeddingsProvider: EmbeddingsProvider
) {
    private val embedder: Embedder = {text -> runBlocking { embeddingsProvider.createEmbedding(text) }}

    /**
     * POST /bits
     *
     * Creates a new Text's bit for a given game.
     *
     * Request Body (application/json):
     * {
     *   "game": "string",      // required - identifier of the game
     *   "text": "string",      // required - the text's bit content (must not be blank)
     *   "inBatch": false       // optional - if true, skips embedding
     * }
     *
     * Responses:
     * - 200 OK:
     *   - Text's bit successfully persisted
     *   - If not in batch mode, attempts embedding (can return warning if it fails)
     * - 400 Bad Request:
     *   - Returned when 'text' is blank
     * - 500 Internal Server Error:
     *   - Returned if text's bit persistence fails
     *
     * Notes:
     * - Embedding is skipped for batch operations
     * - Embedding failure doesn't stop text's bit creation
     */
    @PostMapping
    fun createTextBit(@RequestBody request: TextBitCreationRequest): ResponseEntity<Any> {
        if (request.originalText.text.isBlank()) {
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
                textBit.original.persist(textBitId, request.inBatch, original = true, languageFailures)
                textBit.translations.forEach {
                    it.persist(textBitId, request.inBatch, original = false, languageFailures)
                }
                val warning = if (languageFailures.isEmpty()) ""
                else " Failed embeddings creation for languages $languageFailures"
                return ok().body(MessageResponse("Text bit successfully persisted!$warning"))
            }
        }
    }

    /**
     * PUT /bits/{id}
     *
     * Replaces the text of an existing Text's bit.
     *
     * Path Variable:
     * - id (UUID) - required - identifier of the text bit to be replaced
     *
     * Request Body (application/json):
     * {
     *   "text": "string",      // required - new content of the text's bit (must not be blank)
     *   "inBatch": false       // optional - if true, skips embedding
     * }
     *
     * Responses:
     * - 200 OK:
     *   - Text's bit successfully replaced
     *   - If not in batch mode, attempts embedding (can return warning if it fails)
     * - 400 Bad Request:
     *   - Returned when 'text' is blank
     * - 404 Not Found:
     *   - Returned when text's bit with given ID doesn't exist
     * - 500 Internal Server Error:
     *   - Returned if lookup or persistence fails
     *
     * Notes:
     * - Embedding is skipped for batch operations
     * - Embedding failure doesn't stop text's bit replacement
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

    private fun Translation.persist(
        textBitId: UUID,
        inBatch: Boolean,
        original: Boolean,
        languageFailures: MutableSet<String>
    ) {
        textBitsService.persistTranslation(
            this,
            textBitId,
            original,
            if (inBatch) null else embedder
        ).onLeft {
            languageFailures.add(language)
        }
    }

}

data class TextBitCreationRequest(
    val game: Game,
    val topic: Topic,
    val originalText: Translation,
    val translations: Collection<Translation> = emptyList(),
    val inBatch: Boolean = false
) {
    fun toTextBit() = TextBit(
        id = UUID.randomUUID(),
        game = game,
        topic = topic,
        original = originalText,
        translations = translations.toSet()
    )
}

data class TextBitTranslationPatchRequest(
    val text: String,
    val language: String,
    val inBatch: Boolean = false
)
