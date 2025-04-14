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
import org.agrfesta.btm.api.model.RuleBitsEmbeddingStatus
import org.agrfesta.btm.api.persistence.TestingRulesBitsRepository
import org.agrfesta.btm.api.persistence.TestingRulesEmbeddingRepository
import org.agrfesta.btm.api.persistence.jdbc.repositories.RulesBitsRepository
import org.agrfesta.btm.api.persistence.jdbc.repositories.RulesEmbeddingRepository
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
    @Autowired private val testRulesBitsRepo: TestingRulesBitsRepository,
    @Autowired private val testRulesEmbeddingsRepo: TestingRulesEmbeddingRepository,
    @Autowired private val rulesBitsRepo: RulesBitsRepository,
    @Autowired private val rulesEmbeddingRepo: RulesEmbeddingRepository,
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

    ///// createRuleBit ////////////////////////////////////////////////////////////////////////////////////////////////

    @TestFactory
    fun `createRuleBit() Creates rule bits only, when inBatch is true or not specified`() = listOf(
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
            val ruleBit = testRulesBitsRepo.findById(uuid)
            ruleBit.shouldNotBeNull()
            ruleBit.embeddingStatus shouldBe RuleBitsEmbeddingStatus.UNEMBEDDED.name
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
        val ruleBit = testRulesBitsRepo.findById(uuid)
        ruleBit.shouldNotBeNull()
        ruleBit.embeddingStatus shouldBe RuleBitsEmbeddingStatus.EMBEDDED.name
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
        val ruleBit = testRulesBitsRepo.findById(uuid)
        ruleBit.shouldNotBeNull()
        ruleBit.embeddingStatus shouldBe RuleBitsEmbeddingStatus.UNEMBEDDED.name
        ruleBit.text shouldBe text
        ruleBit.game shouldBe Game.MAUSRITTER.name
        ruleBit.createdOn shouldBe now
        ruleBit.updatedOn.shouldBeNull()
        val ruleEmbedding = testRulesEmbeddingsRepo.findByRuleBitId(uuid)
        ruleEmbedding.shouldBeNull()
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    ///// replaceRuleBit ///////////////////////////////////////////////////////////////////////////////////////////////

    @TestFactory
    fun `replaceRuleBit() Replace rule bits text and remove embedding, when inBatch is true or not specified`() =
        listOf(
        """{"text": "$text", "inBatch": true}""",
        """{"text": "$text"}"""
    ).map {
        dynamicTest(" -> '$it'") {
            val uuid: UUID = UUID.randomUUID()
            val originalText = aRandomUniqueString()
            val creationTime = now.minusSeconds(50_000)
            rulesBitsRepo.insert(uuid, Game.MAUSRITTER, originalText, creationTime)
            val embeddingId: UUID = UUID.randomUUID()
            val embedding = anEmbedding()
            rulesEmbeddingRepo.insertRuleEmbedding(embeddingId, uuid, Game.MAUSRITTER.name, embedding,
                originalText, now)

            val result = given()
                .contentType(ContentType.JSON)
                .body(it)
                .`when`()
                .put("/rules/bits/$uuid")
                .then()
                .statusCode(200)
                .extract()
                .`as`(MessageResponse::class.java)

            result.message shouldBe "Rule bit $uuid successfully replaced!"
            val ruleBit = testRulesBitsRepo.findById(uuid)
            ruleBit.shouldNotBeNull()
            ruleBit.embeddingStatus shouldBe RuleBitsEmbeddingStatus.UNEMBEDDED.name
            ruleBit.text shouldBe text
            ruleBit.game shouldBe Game.MAUSRITTER.name
            ruleBit.createdOn shouldBe creationTime
            ruleBit.updatedOn shouldBe now
            val ruleEmbedding = testRulesEmbeddingsRepo.findByRuleBitId(uuid)
            ruleEmbedding.shouldBeNull()
        }
    }

    @Test fun `replaceRuleBit() Returns 404 when rule bit is missing`() {
        testRulesBitsRepo.findById(uuid).shouldBeNull()

        val result = given()
            .contentType(ContentType.JSON)
            .body("""{"text": "$text", "inBatch": false}""")
            .`when`()
            .put("/rules/bits/$uuid")
            .then()
            .statusCode(404)
            .extract()
            .`as`(MessageResponse::class.java)

        result.message shouldBe "Rule bit $uuid is missing!"
        testRulesBitsRepo.findById(uuid).shouldBeNull()
    }

    @Test fun `replaceRuleBit() Replace rule bit text and embedding when inBatch is false`() {
        val originalText = aRandomUniqueString()
        val embedding = anEmbedding()
        val creationTime = now.minusSeconds(50_000)
        rulesBitsRepo.insert(uuid, Game.MAUSRITTER, originalText, creationTime)
        val embeddingId: UUID = UUID.randomUUID()
        rulesEmbeddingRepo.insertRuleEmbedding(embeddingId, uuid, Game.MAUSRITTER.name, embedding,
            originalText, now)
        coEvery { embeddingsService.createEmbedding(text) } returns embedding.right()

        val result = given()
            .contentType(ContentType.JSON)
            .body("""{"text": "$text", "inBatch": false}""")
            .`when`()
            .put("/rules/bits/$uuid")
            .then()
            .statusCode(200)
            .extract()
            .`as`(MessageResponse::class.java)

        result.message shouldBe "Rule bit $uuid successfully replaced!"
        val ruleBit = testRulesBitsRepo.findById(uuid)
        ruleBit.shouldNotBeNull()
        ruleBit.embeddingStatus shouldBe RuleBitsEmbeddingStatus.EMBEDDED.name
        ruleBit.text shouldBe text
        ruleBit.game shouldBe Game.MAUSRITTER.name
        ruleBit.createdOn shouldBe creationTime
        ruleBit.updatedOn shouldBe now
        val ruleEmbedding = testRulesEmbeddingsRepo.findByRuleBitId(uuid)
        ruleEmbedding.shouldNotBeNull()
        ruleEmbedding.text shouldBe text
        ruleEmbedding.game shouldBe Game.MAUSRITTER.name
        ruleEmbedding.createdOn shouldBe now
        ruleEmbedding.vector shouldBe embedding
    }

    @Test fun `replaceRuleBit() Replace rule bit text and remove embedding when inBatch is false and embedding creation fails`() {
        val originalText = aRandomUniqueString()
        val embedding = anEmbedding()
        val creationTime = now.minusSeconds(50_000)
        rulesBitsRepo.insert(uuid, Game.MAUSRITTER, originalText, creationTime)
        val embeddingId: UUID = UUID.randomUUID()
        rulesEmbeddingRepo.insertRuleEmbedding(embeddingId, uuid, Game.MAUSRITTER.name, embedding,
            originalText, now)
        coEvery { embeddingsService.createEmbedding(text) } returns EmbeddingCreationFailure.left()

        val result = given()
            .contentType(ContentType.JSON)
            .body("""{"text": "$text", "inBatch": false}""")
            .`when`()
            .put("/rules/bits/$uuid")
            .then()
            .statusCode(200)
            .extract()
            .`as`(MessageResponse::class.java)

        result.message shouldBe "Rule bit $uuid successfully replaced! But embedding creation failed!"
        val ruleBit = testRulesBitsRepo.findById(uuid)
        ruleBit.shouldNotBeNull()
        ruleBit.embeddingStatus shouldBe RuleBitsEmbeddingStatus.UNEMBEDDED.name
        ruleBit.text shouldBe text
        ruleBit.game shouldBe Game.MAUSRITTER.name
        ruleBit.createdOn shouldBe creationTime
        ruleBit.updatedOn shouldBe now
        val ruleEmbedding = testRulesEmbeddingsRepo.findByRuleBitId(uuid)
        ruleEmbedding.shouldBeNull()
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

}
