package org.agrfesta.btm.api.controllers

import arrow.core.Either.Left
import arrow.core.Either.Right
import kotlinx.coroutines.runBlocking
import org.agrfesta.btm.api.model.Game
import org.agrfesta.btm.api.persistence.RulesEmbeddingsDao
import org.agrfesta.btm.api.services.EmbeddingsService
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.ResponseEntity
import org.springframework.http.ResponseEntity.ok
import org.springframework.http.ResponseEntity.status
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.*

@RestController
@RequestMapping("/embeddings")
class EmbeddingsController(
    private val embeddingsService: EmbeddingsService,
    private val rulesEmbeddingsDao: RulesEmbeddingsDao
) {

    @PostMapping("/rules")
    fun createRuleEmbedding(@RequestBody request: RulesEmbeddingCreationRequest): ResponseEntity<Any> {
        return if (request.text.isBlank()) {
            status(BAD_REQUEST).body(MessageResponse("Text must not be empty!"))
        } else {
            when (val result = runBlocking { embeddingsService.createEmbedding(request.text) }) {
                is Right -> when(val persisted = rulesEmbeddingsDao.persist(
                    ruleBitId = UUID.randomUUID(),
                    game = request.game,
                    embedding = result.value,
                    text = request.text
                )) {
                    is Right -> ok().body(MessageResponse("Embedding ${persisted.value} successfully persisted!"))
                    is Left -> status(INTERNAL_SERVER_ERROR).body(MessageResponse("Unable to persist embedding!"))
                }
                is Left -> status(INTERNAL_SERVER_ERROR).body(MessageResponse("Unable to create embedding!"))
            }
        }
    }

}

data class RulesEmbeddingCreationRequest(
    val game: Game,
    val text: String
)
