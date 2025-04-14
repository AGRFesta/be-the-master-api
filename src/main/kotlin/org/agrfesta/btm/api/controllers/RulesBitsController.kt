package org.agrfesta.btm.api.controllers

import arrow.core.Either.Left
import arrow.core.Either.Right
import org.agrfesta.btm.api.model.EmbeddingCreationFailure
import org.agrfesta.btm.api.model.Game
import org.agrfesta.btm.api.model.PersistenceFailure
import org.agrfesta.btm.api.persistence.RulesBitsDao
import org.agrfesta.btm.api.services.RulesService
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
@RequestMapping("/rules")
class RulesBitsController(
    private val rulesService: RulesService,
    private val rulesBitsDao: RulesBitsDao
) {

    /**
     * POST /bits
     *
     * Creates a new Rule's bit for a given game.
     *
     * Request Body (application/json):
     * {
     *   "game": "string",      // required - identifier of the game
     *   "text": "string",      // required - the rule's bit content (must not be blank)
     *   "inBatch": false       // optional - if true, skips embedding
     * }
     *
     * Responses:
     * - 200 OK:
     *   - Rule's bit successfully persisted
     *   - If not in batch mode, attempts embedding (can return warning if it fails)
     * - 400 Bad Request:
     *   - Returned when 'text' is blank
     * - 500 Internal Server Error:
     *   - Returned if rule's bit persistence fails
     *
     * Notes:
     * - Embedding is skipped for batch operations
     * - Embedding failure doesn't stop rule's bit creation
     */
    @PostMapping("/bits")
    fun createRuleBit(@RequestBody request: RuleBitCreationRequest): ResponseEntity<Any> {
        if (request.text.isBlank()) {
            return badRequest().body(MessageResponse("Text must not be empty!"))
        }
        when (val insertResult = rulesBitsDao.persist(request.game, request.text)) {
            is Left -> return status(INTERNAL_SERVER_ERROR).body(MessageResponse("Unable to create rule bit!"))
            is Right -> {
                if (!request.inBatch) {
                    val embedResult = rulesService.embedRuleBit(insertResult.value, request.game, request.text)
                    if (embedResult.isLeft()) {
                        return ok().body(MessageResponse(
                            "Rule bit ${insertResult.value} successfully persisted! But embedding creation failed!"))
                    }
                }
                return ok().body(MessageResponse("Rule bit ${insertResult.value} successfully persisted!"))
            }
        }
    }

    /**
     * PUT /bits/{id}
     *
     * Replaces the text of an existing Rule's bit.
     *
     * Path Variable:
     * - id (UUID) - required - identifier of the rule bit to be replaced
     *
     * Request Body (application/json):
     * {
     *   "text": "string",      // required - new content of the rule's bit (must not be blank)
     *   "inBatch": false       // optional - if true, skips embedding
     * }
     *
     * Responses:
     * - 200 OK:
     *   - Rule's bit successfully replaced
     *   - If not in batch mode, attempts embedding (can return warning if it fails)
     * - 400 Bad Request:
     *   - Returned when 'text' is blank
     * - 404 Not Found:
     *   - Returned when rule's bit with given ID doesn't exist
     * - 500 Internal Server Error:
     *   - Returned if lookup or persistence fails
     *
     * Notes:
     * - Embedding is skipped for batch operations
     * - Embedding failure doesn't stop rule's bit replacement
     */
    @PutMapping("/bits/{id}")
    fun replaceRuleBit(@PathVariable id: UUID, @RequestBody request: RuleBitReplacementRequest): ResponseEntity<Any> {
        if (request.text.isBlank()) {
            return badRequest().body(MessageResponse("Text must not be empty!"))
        }
        val ruleBit = try {
            rulesService.findRuleBit(id)
                ?: return status(404).body(MessageResponse("Rule bit $id is missing!"))
        } catch (e: Exception) {
            return internalServerError()
                .body(MessageResponse("Unable to replace rule bit $id!"))
        }
        return when(val result = rulesService.replaceRuleBit(id, ruleBit.game, request.text, request.inBatch)) {
            is Left -> when(result.value) {
                EmbeddingCreationFailure -> ok()
                    .body(MessageResponse("Rule bit $id successfully replaced! But embedding creation failed!"))
                is PersistenceFailure -> internalServerError()
                    .body(MessageResponse("Unable to replace rule bit $id!"))
            }
            is Right -> ok().body(MessageResponse("Rule bit $id successfully replaced!"))
        }
    }

}

data class RuleBitCreationRequest(
    val game: Game,
    val text: String,
    val inBatch: Boolean = true
)

data class RuleBitReplacementRequest(
    val text: String,
    val inBatch: Boolean = true
)
