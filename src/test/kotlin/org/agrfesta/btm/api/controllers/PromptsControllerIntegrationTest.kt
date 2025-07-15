package org.agrfesta.btm.api.controllers

import arrow.core.right
import com.ninjasquad.springmockk.MockkBean
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.mockk.coEvery
import io.mockk.every
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.agrfesta.btm.api.model.Game
import org.agrfesta.btm.api.persistence.jdbc.repositories.GlossariesRepository
import org.agrfesta.btm.api.services.EmbeddingsProvider
import org.agrfesta.btm.api.services.Tokenizer
import org.agrfesta.btm.api.services.utils.toNoNanoSec
import org.agrfesta.test.mothers.aGlossaryEntry
import org.agrfesta.test.mothers.aRandomUniqueString
import org.agrfesta.test.mothers.anEmbedding
import org.agrfesta.test.mothers.generateVectorWithDistance
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import java.time.Instant

class PromptsControllerIntegrationTest(
    @Value("\${translation.prompt.introduction}") private val transPromptIntro: String,
    @Value("\${translation.prompt.original.text.introduction}") private val originalTextIntro: String,
    @Value("\${translation.prompt.suggested.glossary.introduction}") private val suggestedGlossaryIntro: String,
    @Autowired private val glossariesRepository: GlossariesRepository,
    @Autowired private val ragAsserter: RagAsserter,
    @Autowired @MockkBean private val tokenizer: Tokenizer,
    @Autowired @MockkBean private val embeddingsProvider: EmbeddingsProvider
): AbstractIntegrationTest(), RagAsserter by ragAsserter {
    private val now = Instant.now().toNoNanoSec()
    private val glossarySectionRegex = """\[(.*?)](.*)""".toRegex()
    private val game = aGame()
    private val topic = aTopic()
    private val language = aSupportedLanguage()
    private val prompt = aRandomUniqueString()

    companion object {
        @Container
        @ServiceConnection
        val postgres = createPostgresContainer()

        @DynamicPropertySource
        @JvmStatic
        fun dynamicProperties(registry: DynamicPropertyRegistry) {
            val transPromptIntro = aRandomUniqueString()
            registry.add("translation.prompt.introduction") { transPromptIntro }
            val originalTextIntro = aRandomUniqueString()
            registry.add("translation.prompt.original.text.introduction") { originalTextIntro }
            val suggestedGlossaryIntro = aRandomUniqueString()
            registry.add("translation.prompt.suggested.glossary.introduction") { suggestedGlossaryIntro }
        }
    }

    ///// enhanceBasicPrompt ///////////////////////////////////////////////////////////////////////////////////////////

    @Test
    fun `enhanceBasicPrompt() Enhances basic prompt trough RAG`() {
        val target = anEmbedding()
        val chunkA = aRandomUniqueString()
        val chunkB = aRandomUniqueString()
        val chunkC = aRandomUniqueString()
        val chunkD = aRandomUniqueString()
        val chunkE = aRandomUniqueString()
        val embeddingA = generateVectorWithDistance(target, 0.25)
        val embeddingB = generateVectorWithDistance(target, 0.02)
        val embeddingC = generateVectorWithDistance(target, 0.01)
        val embeddingD = generateVectorWithDistance(target, 0.29)
        // Following will be excluded because, by default, distance should be less than 0.3
        val embeddingE = generateVectorWithDistance(target, 0.59)
        val requestJson = aBasicPromptEnhanceRequestJson(
            prompt = prompt,
            game = game.name,
            topic = topic.name,
            language = language.name,
            maxTokens = 600
        )
        givenChunkEmbedding(game, topic, language = language.name, text = chunkA, embeddingA)
        givenChunkEmbedding(game, topic, language = language.name, text = chunkB, embeddingB)
        givenChunkEmbedding(game, topic, language = language.name, text = chunkC, embeddingC)
        givenChunkEmbedding(game, topic, language = language.name, text = chunkD, embeddingD)
        givenChunkEmbedding(game, topic, language = language.name, text = chunkE, embeddingE)
        coEvery { embeddingsProvider.createEmbedding(prompt) } returns target.right()
        every { tokenizer.countTokens(chunkC) } returns 300.right() // first
        every { tokenizer.countTokens(chunkB) } returns 150.right() // second
        every { tokenizer.countTokens(chunkA) } returns 350.right() // third, EXCLUDED, not enough tokens remained
        every { tokenizer.countTokens(chunkD) } returns 10.right()  // fourth, EXCLUDED, not enough tokens remained (actually can be included, we should consider it as improvement)

        val result = given()
            .contentType(ContentType.JSON)
            .body(requestJson)
            .`when`()
            .post("/prompts/enhance/basic")
            .then()
            .statusCode(200)
            .extract().asString()

        val regex = Regex("""\[(.*?)]""", RegexOption.DOT_MATCHES_ALL)
        val matches = regex.findAll(result).toList()
        val question = matches.getOrNull(0)?.groups?.get(1)?.value
        question shouldBe prompt
        val context = matches.getOrNull(1)?.groups?.get(1)?.value
        context shouldBe "$chunkC\n$chunkB"
    }

    @Test
    fun `enhanceBasicPrompt() Enhances basic prompt with no chunks when first is too big`() {
        val target = anEmbedding()
        val chunkA = aRandomUniqueString()
        val chunkB = aRandomUniqueString()
        val embeddingA = generateVectorWithDistance(target, 0.02)
        val embeddingB = generateVectorWithDistance(target, 0.25)
        val requestJson = aBasicPromptEnhanceRequestJson(
            prompt = prompt,
            game = game.name,
            topic = topic.name,
            language = language.name,
            maxTokens = 600
        )
        givenChunkEmbedding(game, topic, language = language.name, text = chunkA, embeddingA)
        givenChunkEmbedding(game, topic, language = language.name, text = chunkB, embeddingB)
        coEvery { embeddingsProvider.createEmbedding(prompt) } returns target.right()
        every { tokenizer.countTokens(chunkB) } returns 1.right()
        every { tokenizer.countTokens(chunkA) } returns 700.right()

        val result = given()
            .contentType(ContentType.JSON)
            .body(requestJson)
            .`when`()
            .post("/prompts/enhance/basic")
            .then()
            .statusCode(200)
            .extract().asString()

        val regex = Regex("""\[(.*?)]""", RegexOption.DOT_MATCHES_ALL)
        val matches = regex.findAll(result).toList()
        val question = matches.getOrNull(0)?.groups?.get(1)?.value
        question shouldBe prompt
        val context = matches.getOrNull(1)?.groups?.get(1)?.value
        context shouldBe ""
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @TestFactory
    fun returnsTokenCountsOfAPrompt() = listOf(
        "" to 0,
        "Nel mezzo del cammin di nostra vita" to 10,
        "mi ritrovai" to 4,
        "per una selva oscura" to 6
    ).map {
        dynamicTest("${it.first} -> ${it.second}") {
            every { tokenizer.countTokens(it.first) } returns it.second.right()

            val result = given()
                .contentType(ContentType.JSON)
                .body("""{"prompt":"${it.first}"}""")
                .`when`()
                .post("/prompts/tokens-count")
                .then()
                .statusCode(200)
                .extract()
                .`as`(TokenCountResponse::class.java)

            result.count shouldBe it.second
        }
    }

    @Test
    fun `createTranslationPrompt() Returns prompt with configured initial introduction`() {
        val result = given()
            .contentType(ContentType.JSON)
            .body("""{"game":"${Game.entries.random()}","text":"${aRandomUniqueString()}"}""")
            .`when`()
            .post("/prompts/translation")
            .then()
            .statusCode(200)
            .extract().asString()

        result shouldStartWith transPromptIntro
    }

    @Test
    fun `createTranslationPrompt() Returns prompt with original text following initial introduction`() {
        val originalText = aRandomUniqueString()
        val result = given()
            .contentType(ContentType.JSON)
            .body("""{"game":"${Game.entries.random()}","text":"$originalText"}""")
            .`when`()
            .post("/prompts/translation")
            .then()
            .statusCode(200)
            .extract().asString()

        val remainingText = result.removePrefix("$transPromptIntro\n$originalTextIntro")
        remainingText shouldStartWith originalText
    }

    @Test
    fun `createTranslationPrompt() Returns prompt with related glossaries entries following original text`() {
        val game = Game.entries.random()
        val entryA = aGlossaryEntry()
        val entryB = aGlossaryEntry()
        val entryC = aGlossaryEntry()
        val entryD = aGlossaryEntry()
        val entryE = aGlossaryEntry()
        glossariesRepository.insertEntry(game, entryA.first, entryA.second, now)
        glossariesRepository.insertEntry(game, entryB.first, entryB.second, now)
        glossariesRepository.insertEntry(game, entryC.first, entryC.second, now)
        glossariesRepository.insertEntry(game, entryD.first, entryD.second, now)
        glossariesRepository.insertEntry(game, entryE.first, entryE.second, now)
        val originalText = "_${entryB.first}_${entryB.first}_${entryD.first.uppercase()}_${entryE.first}"
        val result = given()
            .contentType(ContentType.JSON)
            .body("""{"game":"${game.name}","text":"$originalText"}""")
            .`when`()
            .post("/prompts/translation")
            .then()
            .statusCode(200)
            .extract().asString()

        val remainingText = result
            .removePrefix("$transPromptIntro\n$originalTextIntro$originalText\n$suggestedGlossaryIntro")
        val suggestedGlossary = remainingText.extractGlossaryEntries()
        suggestedGlossary.shouldNotBeNull()
        val expectedSuggestedGlossary = listOf(entryE, entryB, entryD).toMap()
        suggestedGlossary.shouldContainExactly(expectedSuggestedGlossary)
    }

    private fun String.extractGlossaryEntries(): Map<String, String>? = glossarySectionRegex.matchEntire(this)
            ?.groupValues?.get(1)
            ?.split(",")
            ?.associate {
                val (key, value) = it.split("=")
                key to value
            }

}
