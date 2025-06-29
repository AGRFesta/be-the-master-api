package org.agrfesta.btm.api.controllers

import arrow.core.Either
import arrow.core.Either.Left
import arrow.core.Either.Right
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import kotlinx.coroutines.runBlocking
import org.agrfesta.btm.api.model.BtmConfigurationFailure
import org.agrfesta.btm.api.model.BtmFlowFailure
import org.agrfesta.btm.api.model.Embedding
import org.agrfesta.btm.api.model.Game
import org.agrfesta.btm.api.model.PersistenceFailure
import org.agrfesta.btm.api.model.SupportedLanguage
import org.agrfesta.btm.api.persistence.EmbeddingsDao
import org.agrfesta.btm.api.persistence.PartiesDao
import org.agrfesta.btm.api.persistence.jdbc.repositories.GlossariesRepository
import org.agrfesta.btm.api.services.ChunksService.Companion.DEFAULT_DISTANCE_LIMIT
import org.agrfesta.btm.api.services.ChunksService.Companion.DEFAULT_EMBEDDINGS_LIMIT
import org.agrfesta.btm.api.services.EmbeddingsProvider
import org.agrfesta.btm.api.services.PromptEnhanceConfiguration
import org.agrfesta.btm.api.services.Tokenizer
import org.agrfesta.btm.api.services.utils.LoggerDelegate
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
    private val config: PromptEnhanceConfiguration,
    private val tokenizer: Tokenizer,
    private val partiesDao: PartiesDao,
    private val embeddingsProvider: EmbeddingsProvider,
    private val embeddingsDao: EmbeddingsDao,
    private val glossariesRepository: GlossariesRepository
) {
    private val logger by LoggerDelegate()

    companion object {
        const val DEFAULT_MAX_TOKENS = 8_000
    }

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

    @PostMapping("/enhance/basic")
    fun enhanceBasicPrompt(@RequestBody request: BasicPromptEnhanceRequest): ResponseEntity<Any> =
        request.validate()
            .flatMap { validated ->
                runBlocking { embeddingsProvider.createEmbedding(validated.prompt) }
                    .flatMap { target ->
                        val result = try {
                            embeddingsDao.searchBySimilarity(target, validated.game, validated.topic,
                                validated.language.name,
                                DEFAULT_EMBEDDINGS_LIMIT,
                                DEFAULT_DISTANCE_LIMIT
                            ).right()
                        } catch (e: Exception) {
                            PersistenceFailure("Search by similarity failed!", e).left()
                        }
                        result.flatMap {
                            enhancePrompt(validated, it)
                        }
                    }
        }.toResponseEntity()

    private fun enhancePrompt(
        request: ValidBasicPromptEnhanceRequest,
        context: List<Pair<String,Double>>
    ): Either<BtmFlowFailure, String> {
        val chunks = context.map { it.first }
            .filterByTokenLimit(request.maxTokens)
        return config.basicTemplate[SupportedLanguage.IT]
            ?.replace("\${context}", chunks.joinToString(separator = "\n"))
            ?.replace("\${prompt}", request.prompt)
            ?.right()
            ?: BtmConfigurationFailure("Unable to find IT basic template").left()
    }

    private fun List<String>.filterByTokenLimit(limit: Int): List<String> =
        scan(0 to "") { (sum, _), str -> tokenizer.countTokensOrIgnore(sum, str) }
            .takeWhile { it.first <= limit }
            .map { it.second }
            .filter { it.isNotBlank() }

    private fun Tokenizer.countTokensOrIgnore(sum: Int, chunk: String): Pair<Int, String> = try {
            val tokens = countTokens(chunk)
            (sum + tokens) to chunk
        } catch (e: Exception) {
            logger.error("chunk token count failure!", e)
            sum to ""
        }

}

data class PromptRequest(val prompt: String)
data class TokenCountResponse(val count: Int)
data class PromptEnhanceRequest(val partyId: UUID, val prompt: String)
data class TranslationPromptRequest(val game: Game, val text: String)

data class BasicPromptEnhanceRequest(
    val prompt: String?,
    val game: String?,
    val topic: String?,
    val language: String?,
    val maxTokens: Int?
)