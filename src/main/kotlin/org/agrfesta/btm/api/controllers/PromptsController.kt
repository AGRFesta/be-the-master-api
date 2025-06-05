package org.agrfesta.btm.api.controllers

import arrow.core.Either.Left
import arrow.core.Either.Right
import kotlinx.coroutines.runBlocking
import org.agrfesta.btm.api.model.Embedding
import org.agrfesta.btm.api.model.Game
import org.agrfesta.btm.api.persistence.PartiesDao
import org.agrfesta.btm.api.persistence.EmbeddingsDao
import org.agrfesta.btm.api.persistence.jdbc.repositories.GlossariesRepository
import org.agrfesta.btm.api.services.EmbeddingsProvider
import org.agrfesta.btm.api.services.Tokenizer
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.OK
import org.springframework.http.ResponseEntity
import org.springframework.http.ResponseEntity.status
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.*

@RestController
@RequestMapping("/prompts")
class PromptsController(
    @Value("\${translation.prompt.introduction}") private val transPromptIntro: String,
    @Value("\${translation.prompt.original.text.introduction}") private val originalTextIntro: String,
    @Value("\${translation.prompt.suggested.glossary.introduction}") private val suggestedGlossaryIntro: String,
    private val tokenizer: Tokenizer,
    private val partiesDao: PartiesDao,
    private val embeddingsProvider: EmbeddingsProvider,
    private val embeddingsDao: EmbeddingsDao,
    private val glossariesRepository: GlossariesRepository
) {

    @PostMapping("/enhance")
    fun enhance(@RequestBody request: PromptEnhanceRequest): ResponseEntity<Any> =
        when (val partyResult = partiesDao.getParty(request.partyId)) {
            is Left -> status(INTERNAL_SERVER_ERROR).body("Failure!")
            is Right -> {
                val party = partyResult.value
                val targetResult = runBlocking { embeddingsProvider.createEmbedding(request.prompt) }
                when (targetResult) {
                    is Left -> status(INTERNAL_SERVER_ERROR).body("Failure!")
                    is Right -> {
                        val target: Embedding = targetResult.value
                        when (val nearestResult = embeddingsDao.nearestChunks(party.game, target)) {
                            is Left -> status(INTERNAL_SERVER_ERROR).body("Failure!")
                            is Right -> {
                                val partySection: String = party.members.joinToString(separator = "\n")
                                val prompt = nearestResult.value.joinToString(separator = "\n")
                                status(OK).body("$partySection\n$prompt")
                            }
                        }
                    }
                }
            }
        }

    @PostMapping("/tokens-count")
    fun tokenCount(@RequestBody request: PromptRequest): ResponseEntity<Any> {
        val count = tokenizer.countTokens(request.prompt)
        return status(OK).body(TokenCountResponse(count))
    }

    @PostMapping("/embedding")
    fun createEmbedding(@RequestBody request: PromptRequest): ResponseEntity<Any> {
        val result = runBlocking { embeddingsProvider.createEmbedding(request.prompt) }
        return when (result) {
            is Left -> status(INTERNAL_SERVER_ERROR).body("Failure!")
            is Right -> status(OK).body(result)
        }
    }

    @PostMapping("/translation")
    fun createTranslationPrompt(@RequestBody request: TranslationPromptRequest): ResponseEntity<Any> {
        val glossary = glossariesRepository.getAllEntriesByGame(request.game)
        val lowerText = request.text.lowercase()
        val suggestedGlossary = glossary.entries
            .map { it.toPair() }
            .filter { lowerText.contains(it.first) }
            .joinToString(",") { "${it.first}=${it.second}" }
        return status(OK)
            .body("$transPromptIntro\n$originalTextIntro${request.text}\n$suggestedGlossaryIntro[$suggestedGlossary]")
    }

}

data class PromptRequest(val prompt: String)
data class TokenCountResponse(val count: Int)
data class PromptEnhanceRequest(val partyId: UUID, val prompt: String)
data class TranslationPromptRequest(val game: Game, val text: String)
