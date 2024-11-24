package org.agrfesta.btm.api.controllers

import arrow.core.left
import arrow.core.right
import com.ninjasquad.springmockk.MockkBean
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.restassured.RestAssured
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.agrfesta.btm.api.model.EmbeddingCreationFailure
import org.agrfesta.btm.api.model.Game
import org.agrfesta.btm.api.model.RuleBitEmbeddingStatus
import org.agrfesta.btm.api.persistence.TestingRulesBitsRepository
import org.agrfesta.btm.api.persistence.TestingRulesEmbeddingRepository
import org.agrfesta.btm.api.services.EmbeddingsService
import org.agrfesta.btm.api.services.utils.RandomGenerator
import org.agrfesta.btm.api.services.utils.TimeService
import org.agrfesta.btm.api.services.utils.toNoNanoSec
import org.agrfesta.test.mothers.aRandomUniqueString
import org.agrfesta.test.mothers.anEmbedding
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.time.Instant
import java.util.*

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class RulesBitsControllerIntegrationTest(
    @Autowired private val testRulesBitsRepository: TestingRulesBitsRepository,
    @Autowired private val testRulesEmbeddingsRepo: TestingRulesEmbeddingRepository,
    @Autowired @MockkBean private val embeddingsService: EmbeddingsService,
    @Autowired @MockkBean private val randomGenerator: RandomGenerator,
    @Autowired @MockkBean private val timeService: TimeService
) {

    companion object {
        @Container
        @ServiceConnection
        val postgres: PostgreSQLContainer<*> = DockerImageName.parse("pgvector/pgvector:pg16")
            .asCompatibleSubstituteFor("postgres")
            .let { PostgreSQLContainer(it) }
    }

    @LocalServerPort
    private val port: Int? = null

    private val uuid: UUID = UUID.randomUUID()
    private val now = Instant.now().toNoNanoSec()
    private val text = aRandomUniqueString()

    @BeforeEach
    fun setUp() {
        RestAssured.baseURI = "http://localhost:$port"

        every { randomGenerator.uuid() } returns uuid
        every { timeService.nowNoNano() } returns now
    }

    @TestFactory
    fun `createRuleBit() Creates rule bit only when inBatch is true`() = listOf(
        """{"game": "MAUSRITTER", "text": "$text", "inBatch": true}""",
        """{"game": "MAUSRITTER", "text": "$text"}"""
    ).map {
        dynamicTest(" -> '$it'") {
            val uuid: UUID = UUID.randomUUID()
            every { randomGenerator.uuid() } returns uuid
            val embedding = anEmbedding()
            coEvery { embeddingsService.createEmbedding(text) } returns embedding.right()

            val result = given()
                .contentType(ContentType.JSON)
                .body(it)
                .`when`()
                .post("/rules/bits")
                .then()
                .statusCode(200)
                .extract()
                .`as`(MessageResponse::class.java)

            result.message shouldBe "Rule bit $uuid successfully persisted!"
            val ruleBit = testRulesBitsRepository.findById(uuid)
            ruleBit.shouldNotBeNull()
            ruleBit.embeddingStatus shouldBe RuleBitEmbeddingStatus.UNEMBEDDED.name
            ruleBit.text shouldBe text
            ruleBit.game shouldBe Game.MAUSRITTER.name
            ruleBit.createdOn shouldBe now
            ruleBit.updatedOn.shouldBeNull()
            val ruleEmbedding = testRulesEmbeddingsRepo.findByRuleBitId(uuid)
            ruleEmbedding.shouldBeNull()
        }
    }

    @Test fun `createRuleBit() Creates rule bit and embedding when inBatch is false`() {
        val text = aRandomUniqueString()
        val embedding = anEmbedding()
        coEvery { embeddingsService.createEmbedding(text) } returns embedding.right()

        val result = given()
            .contentType(ContentType.JSON)
            .body("""{"game": "MAUSRITTER", "text": "$text", "inBatch": false}""")
            .`when`()
            .post("/rules/bits")
            .then()
            .statusCode(200)
            .extract()
            .`as`(MessageResponse::class.java)

        result.message shouldBe "Rule bit $uuid successfully persisted!"
        val ruleBit = testRulesBitsRepository.findById(uuid)
        ruleBit.shouldNotBeNull()
        ruleBit.embeddingStatus shouldBe RuleBitEmbeddingStatus.EMBEDDED.name
        ruleBit.text shouldBe text
        ruleBit.game shouldBe Game.MAUSRITTER.name
        ruleBit.createdOn shouldBe now
        ruleBit.updatedOn shouldBe now
        val ruleEmbedding = testRulesEmbeddingsRepo.findByRuleBitId(uuid)
        ruleEmbedding.shouldNotBeNull()
        ruleEmbedding.text shouldBe text
        ruleEmbedding.game shouldBe Game.MAUSRITTER.name
        ruleEmbedding.createdOn shouldBe now
        ruleEmbedding.vector shouldBe embedding
    }

    @Test fun `createRuleBit() Creates rule bit but not embedding when inBatch is false and embedding creation fails`() {
        val text = aRandomUniqueString()
        coEvery { embeddingsService.createEmbedding(text) } returns EmbeddingCreationFailure.left()

        val result = given()
            .contentType(ContentType.JSON)
            .body("""{"game": "MAUSRITTER", "text": "$text", "inBatch": false}""")
            .`when`()
            .post("/rules/bits")
            .then()
            .statusCode(200)
            .extract()
            .`as`(MessageResponse::class.java)

        result.message shouldBe "Rule bit $uuid successfully persisted! But embedding creation failed!"
        val ruleBit = testRulesBitsRepository.findById(uuid)
        ruleBit.shouldNotBeNull()
        ruleBit.embeddingStatus shouldBe RuleBitEmbeddingStatus.UNEMBEDDED.name
        ruleBit.text shouldBe text
        ruleBit.game shouldBe Game.MAUSRITTER.name
        ruleBit.createdOn shouldBe now
        ruleBit.updatedOn.shouldBeNull()
        val ruleEmbedding = testRulesEmbeddingsRepo.findByRuleBitId(uuid)
        ruleEmbedding.shouldBeNull()
    }

}
