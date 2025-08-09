package org.agrfesta.btm.api.controllers

import arrow.core.Either
import arrow.core.Either.Left
import arrow.core.Either.Right
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import java.util.*
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

/**
 * REST controller for managing prompt-related operations such as enhancement, token counting,
 * embeddings, and glossary-based translation support.
 */
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

    /**
     * POST /prompts/enhance
     *
     * Enhances a prompt by retrieving the most similar chunks for the given party and appending them to the prompt.
     * This enhancement uses nearest embedding search and contextual party data.
     *
     * @param request the [PromptEnhanceRequest] containing the party ID and original prompt.
     * @return 200 OK with enhanced prompt string if successful,
     *         500 Internal Server Error in case of failure at any stage.
     */
    @PostMapping("/enhance")
    fun enhance(@RequestBody request: PromptEnhanceRequest): ResponseEntity<Any> =
        when (val partyResult = partiesDao.getParty(request.partyId)) {
            is Left -> status(INTERNAL_SERVER_ERROR).body("Failure!")
            is Right -> {
                val party = partyResult.value
                val targetResult = runBlocking { embeddingsProvider.createEmbedding(request.prompt, true) }
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

    /**
     * POST /prompts/tokens-count
     *
     * Calculates the number of tokens in a given prompt using the configured tokenizer.
     *
     * @param request the [PromptRequest] containing the prompt string.
     * @return 200 OK with [TokenCountResponse] containing the number of tokens.
     *          500 Internal Server Error if tokens count fails.
     */
    @PostMapping("/tokens-count")
    fun tokenCount(@RequestBody request: PromptRequest): ResponseEntity<Any> =
        when (val count = runBlocking { tokenizer.countTokens(request.prompt, true) } ) {
            is Left -> status(INTERNAL_SERVER_ERROR).body("Failure!")
            is Right -> status(OK).body(TokenCountResponse(count.value))
        }

    /**
     * POST /prompts/embedding
     *
     * Generates an embedding vector for the given prompt text.
     *
     * @param request the [PromptRequest] containing the prompt string.
     * @return 200 OK with the embedding on success,
     *         500 Internal Server Error if the embedding fails to be created.
     */
    @PostMapping("/embedding")
    fun createEmbedding(@RequestBody request: PromptRequest): ResponseEntity<Any> {
        val result = runBlocking { embeddingsProvider.createEmbedding(request.prompt, true) }
        return when (result) {
            is Left -> status(INTERNAL_SERVER_ERROR).body("Failure!")
            is Right -> status(OK).body(result)
        }
    }

    /**
     * POST /prompts/translation
     *
     * Generates a translation prompt for the given game and text, enhanced by glossary suggestions
     * based on term matches found within the text.
     *
     * @param request the [TranslationPromptRequest] including game and text to translate.
     * @return 200 OK with a translation prompt containing introduction, original text, and suggested glossary entries.
     */
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

    /**
     * POST /prompts/enhance/basic
     *
     * Enhances a prompt using basic template logic. It finds similar chunks by embedding similarity,
     * filters them by a token limit, and inserts them into a language-specific template.
     *
     * @param request the [BasicPromptEnhanceRequest] containing prompt, context info, and token constraints.
     * @return 200 OK with the final enhanced prompt, or error response in case of validation or processing failure.
     */
    @PostMapping("/enhance/basic")
    fun enhanceBasicPrompt(@RequestBody request: BasicPromptEnhanceRequest): ResponseEntity<Any> =
        request.validate()
            .flatMap { validated ->
                runBlocking {
                    logger.info("Creating prompt embedding...")
                    embeddingsProvider.createEmbedding(validated.prompt, true)
                }
                    .flatMap { target ->
                        val result = try {
                            logger.info("Searching similar chunks...")
                            embeddingsDao.searchBySimilarity(target, validated.game, validated.topic,
                                validated.language.name,
                                DEFAULT_EMBEDDINGS_LIMIT,
                                DEFAULT_DISTANCE_LIMIT
                            ).also {
                                logger.info("Found ${it.size} chunks")
                            }.right()
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

    private fun Tokenizer.countTokensOrIgnore(sum: Int, chunk: String): Pair<Int, String> =
        runBlocking {
            countTokens(chunk, true).fold(
                ifLeft = {
                    logger.error("chunk token count failure!")
                    sum to ""
                },
                ifRight = { (sum + it) to chunk }
            )
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