package org.agrfesta.btm.api.controllers

import arrow.core.Either.Left
import arrow.core.Either.Right
import org.agrfesta.btm.api.model.EmbeddingCreationFailure
import org.agrfesta.btm.api.model.Game
import org.agrfesta.btm.api.model.PersistenceFailure
import org.agrfesta.btm.api.model.Topic
import org.agrfesta.btm.api.persistence.TextBitsDao
import org.agrfesta.btm.api.services.TextBitsService
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.ResponseEntity
import org.springframework.http.ResponseEntity.badRequest
import org.springframework.http.ResponseEntity.internalServerError
import org.springframework.http.ResponseEntity.ok
import org.springframework.http.ResponseEntity.status
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.*

@RestController
@RequestMapping("/text-bits")
class TextBitsController(
    private val textBitsService: TextBitsService,
    private val textBitsDao: TextBitsDao
) {

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
        if (request.text.isBlank()) {
            return badRequest().body(MessageResponse("Text must not be empty!"))
        }
        when (val insertResult = textBitsDao.persist(request.game, request.text, request.topic)) {
            is Left -> return status(INTERNAL_SERVER_ERROR).body(MessageResponse("Unable to create text bit!"))
            is Right -> {
                if (!request.inBatch) {
                    val embedResult = textBitsService.embedTextBit(insertResult.value, request.game, request.text)
                    if (embedResult.isLeft()) {
                        return ok().body(MessageResponse(
                            "Text bit ${insertResult.value} successfully persisted! But embedding creation failed!"))
                    }
                }
                return ok().body(MessageResponse("Text bit ${insertResult.value} successfully persisted!"))
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
    @PutMapping("/{id}")
    fun replaceTextBit(@PathVariable id: UUID, @RequestBody request: TextBitReplacementRequest): ResponseEntity<Any> {
        if (request.text.isBlank()) {
            return badRequest().body(MessageResponse("Text must not be empty!"))
        }
        val textBit = try {
            textBitsService.findTextBit(id)
                ?: return status(404).body(MessageResponse("Text bit $id is missing!"))
        } catch (e: Exception) {
            return internalServerError()
                .body(MessageResponse("Unable to replace text bit $id!"))
        }
        return when(val result = textBitsService.replaceTextBit(id, textBit.game, request.text, request.inBatch)) {
            is Left -> when(result.value) {
                EmbeddingCreationFailure -> ok()
                    .body(MessageResponse("Text bit $id successfully replaced! But embedding creation failed!"))
                is PersistenceFailure -> internalServerError()
                    .body(MessageResponse("Unable to replace text bit $id!"))
            }
            is Right -> ok().body(MessageResponse("Text bit $id successfully replaced!"))
        }
    }

}

data class TextBitCreationRequest(
    val game: Game,
    val text: String,
    val topic: Topic,
    val inBatch: Boolean = true
)

data class TextBitReplacementRequest(
    val text: String,
    val inBatch: Boolean = true
)
