package org.agrfesta.btm.api.controllers

import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.restassured.RestAssured
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.agrfesta.btm.api.model.Game
import org.agrfesta.btm.api.persistence.jdbc.repositories.GlossariesRepository
import org.agrfesta.btm.api.services.utils.toNoNanoSec
import org.agrfesta.test.mothers.aGlossaryEntry
import org.agrfesta.test.mothers.aRandomUniqueString
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.time.Instant

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class PromptsControllerIntegrationTest(
    @Value("\${translation.prompt.introduction}") private val transPromptIntro: String,
    @Value("\${translation.prompt.original.text.introduction}") private val originalTextIntro: String,
    @Value("\${translation.prompt.suggested.glossary.introduction}") private val suggestedGlossaryIntro: String,
    @Autowired private val glossariesRepository: GlossariesRepository
) {
    private val now = Instant.now().toNoNanoSec()
    private val glossarySectionRegex = """\[(.*?)](.*)""".toRegex()

    companion object {
        @Container
        @ServiceConnection
        val postgres: PostgreSQLContainer<*> = DockerImageName.parse("pgvector/pgvector:pg16")
            .asCompatibleSubstituteFor("postgres")
            .let { PostgreSQLContainer(it) }

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

    @LocalServerPort private val port: Int? = null

    @BeforeEach
    fun setUp() {
        RestAssured.baseURI = "http://localhost:$port"
    }

    @TestFactory
    fun returnsTokenCountsOfAPrompt() = listOf(
        "" to 0,
        "Nel mezzo del cammin di nostra vita" to 10,
        "mi ritrovai" to 4,
        "per una selva oscura" to 6
    ).map {
        dynamicTest("${it.first} -> ${it.second}") {
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
