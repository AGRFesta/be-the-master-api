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
import org.agrfesta.btm.api.model.TextBitEmbeddingStatus
import org.agrfesta.btm.api.model.Topic
import org.agrfesta.btm.api.persistence.TestingTextBitsRepository
import org.agrfesta.btm.api.persistence.TestingEmbeddingRepository
import org.agrfesta.btm.api.persistence.jdbc.repositories.TextBitsRepository
import org.agrfesta.btm.api.persistence.jdbc.repositories.EmbeddingRepository
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
class TextBitsControllerIntegrationTest(
    @Autowired private val testTextBitsRepo: TestingTextBitsRepository,
    @Autowired private val testEmbeddingsRepo: TestingEmbeddingRepository,
    @Autowired private val textBitsRepo: TextBitsRepository,
    @Autowired private val embeddingRepo: EmbeddingRepository,
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
    private val topic = Topic.RULE

    @BeforeEach
    fun setUp() {
        RestAssured.baseURI = "http://localhost:$port"

        every { randomGenerator.uuid() } returns uuid
        every { timeService.nowNoNano() } returns now
    }

    ///// createTextBit ////////////////////////////////////////////////////////////////////////////////////////////////

    @TestFactory
    fun `createTextBit() Creates text bits only, when inBatch is true or not specified`() = listOf(
        """{"game": "MAUSRITTER", "text": "$text", "topic": "$topic", "inBatch": true}""",
        """{"game": "MAUSRITTER", "text": "$text", "topic": "$topic"}"""
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
                .post("/text-bits")
                .then()
                .statusCode(200)
                .extract()
                .`as`(MessageResponse::class.java)

            result.message shouldBe "Text bit $uuid successfully persisted!"
            val textBit = testTextBitsRepo.findById(uuid)
            textBit.shouldNotBeNull()
            textBit.embeddingStatus shouldBe TextBitEmbeddingStatus.UNEMBEDDED.name
            textBit.text shouldBe text
            textBit.game shouldBe Game.MAUSRITTER.name
            textBit.createdOn shouldBe now
            textBit.updatedOn.shouldBeNull()
            val persistedEmbedding = testEmbeddingsRepo.findByTextBitId(uuid)
            persistedEmbedding.shouldBeNull()
        }
    }

    @Test fun `createTextBit() Creates text bit and embedding when inBatch is false`() {
        val text = aRandomUniqueString()
        val embedding = anEmbedding()
        coEvery { embeddingsService.createEmbedding(text) } returns embedding.right()

        val result = given()
            .contentType(ContentType.JSON)
            .body("""{"game": "MAUSRITTER", "text": "$text", "topic": "$topic", "inBatch": false}""")
            .`when`()
            .post("/text-bits")
            .then()
            .statusCode(200)
            .extract()
            .`as`(MessageResponse::class.java)

        result.message shouldBe "Text bit $uuid successfully persisted!"
        val textBit = testTextBitsRepo.findById(uuid)
        textBit.shouldNotBeNull()
        textBit.embeddingStatus shouldBe TextBitEmbeddingStatus.EMBEDDED.name
        textBit.text shouldBe text
        textBit.game shouldBe Game.MAUSRITTER.name
        textBit.createdOn shouldBe now
        textBit.updatedOn shouldBe now
        val persistedEmbedding = testEmbeddingsRepo.findByTextBitId(uuid)
        persistedEmbedding.shouldNotBeNull()
        persistedEmbedding.text shouldBe text
        persistedEmbedding.game shouldBe Game.MAUSRITTER.name
        persistedEmbedding.createdOn shouldBe now
        persistedEmbedding.vector shouldBe embedding
    }

    @Test fun `createTextBit() Creates text bit but not embedding when inBatch is false and embedding creation fails`() {
        val text = aRandomUniqueString()
        coEvery { embeddingsService.createEmbedding(text) } returns EmbeddingCreationFailure.left()

        val result = given()
            .contentType(ContentType.JSON)
            .body("""{"game": "MAUSRITTER", "text": "$text", "topic": "$topic", "inBatch": false}""")
            .`when`()
            .post("/text-bits")
            .then()
            .statusCode(200)
            .extract()
            .`as`(MessageResponse::class.java)

        result.message shouldBe "Text bit $uuid successfully persisted! But embedding creation failed!"
        val textBit = testTextBitsRepo.findById(uuid)
        textBit.shouldNotBeNull()
        textBit.embeddingStatus shouldBe TextBitEmbeddingStatus.UNEMBEDDED.name
        textBit.text shouldBe text
        textBit.game shouldBe Game.MAUSRITTER.name
        textBit.createdOn shouldBe now
        textBit.updatedOn.shouldBeNull()
        val persistedEmbedding = testEmbeddingsRepo.findByTextBitId(uuid)
        persistedEmbedding.shouldBeNull()
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    ///// replaceTextBit ///////////////////////////////////////////////////////////////////////////////////////////////

    @TestFactory
    fun `replaceTextBit() Replace text bits text and remove embedding, when inBatch is true or not specified`() =
        listOf(
        """{"text": "$text", "inBatch": true}""",
        """{"text": "$text"}"""
    ).map {
        dynamicTest(" -> '$it'") {
            val uuid: UUID = UUID.randomUUID()
            val originalText = aRandomUniqueString()
            val creationTime = now.minusSeconds(50_000)
            textBitsRepo.insert(uuid, Game.MAUSRITTER, originalText, topic, creationTime)
            val embeddingId: UUID = UUID.randomUUID()
            val embedding = anEmbedding()
            embeddingRepo.insertEmbedding(embeddingId, uuid, Game.MAUSRITTER.name, embedding,
                originalText, now)

            val result = given()
                .contentType(ContentType.JSON)
                .body(it)
                .`when`()
                .put("/text-bits/$uuid")
                .then()
                .statusCode(200)
                .extract()
                .`as`(MessageResponse::class.java)

            result.message shouldBe "Text bit $uuid successfully replaced!"
            val textBit = testTextBitsRepo.findById(uuid)
            textBit.shouldNotBeNull()
            textBit.embeddingStatus shouldBe TextBitEmbeddingStatus.UNEMBEDDED.name
            textBit.text shouldBe text
            textBit.game shouldBe Game.MAUSRITTER.name
            textBit.createdOn shouldBe creationTime
            textBit.updatedOn shouldBe now
            val persistedEmbedding = testEmbeddingsRepo.findByTextBitId(uuid)
            persistedEmbedding.shouldBeNull()
        }
    }

    @Test fun `replaceTextBit() Returns 404 when text bit is missing`() {
        testTextBitsRepo.findById(uuid).shouldBeNull()

        val result = given()
            .contentType(ContentType.JSON)
            .body("""{"text": "$text", "inBatch": false}""")
            .`when`()
            .put("/text-bits/$uuid")
            .then()
            .statusCode(404)
            .extract()
            .`as`(MessageResponse::class.java)

        result.message shouldBe "Text bit $uuid is missing!"
        testTextBitsRepo.findById(uuid).shouldBeNull()
    }

    @Test fun `replaceTextBit() Replace text bit text and embedding when inBatch is false`() {
        val originalText = aRandomUniqueString()
        val embedding = anEmbedding()
        val creationTime = now.minusSeconds(50_000)
        textBitsRepo.insert(uuid, Game.MAUSRITTER, originalText, topic, creationTime)
        val embeddingId: UUID = UUID.randomUUID()
        embeddingRepo.insertEmbedding(embeddingId, uuid, Game.MAUSRITTER.name, embedding,
            originalText, now)
        coEvery { embeddingsService.createEmbedding(text) } returns embedding.right()

        val result = given()
            .contentType(ContentType.JSON)
            .body("""{"text": "$text", "inBatch": false}""")
            .`when`()
            .put("/text-bits/$uuid")
            .then()
            .statusCode(200)
            .extract()
            .`as`(MessageResponse::class.java)

        result.message shouldBe "Text bit $uuid successfully replaced!"
        val textBit = testTextBitsRepo.findById(uuid)
        textBit.shouldNotBeNull()
        textBit.embeddingStatus shouldBe TextBitEmbeddingStatus.EMBEDDED.name
        textBit.text shouldBe text
        textBit.game shouldBe Game.MAUSRITTER.name
        textBit.createdOn shouldBe creationTime
        textBit.updatedOn shouldBe now
        val persistedEmbedding = testEmbeddingsRepo.findByTextBitId(uuid)
        persistedEmbedding.shouldNotBeNull()
        persistedEmbedding.text shouldBe text
        persistedEmbedding.game shouldBe Game.MAUSRITTER.name
        persistedEmbedding.createdOn shouldBe now
        persistedEmbedding.vector shouldBe embedding
    }

    @Test fun `replaceTextBit() Replace text bit text and remove embedding when inBatch is false and embedding creation fails`() {
        val originalText = aRandomUniqueString()
        val embedding = anEmbedding()
        val creationTime = now.minusSeconds(50_000)
        textBitsRepo.insert(uuid, Game.MAUSRITTER, originalText, topic, creationTime)
        val embeddingId: UUID = UUID.randomUUID()
        embeddingRepo.insertEmbedding(embeddingId, uuid, Game.MAUSRITTER.name, embedding,
            originalText, now)
        coEvery { embeddingsService.createEmbedding(text) } returns EmbeddingCreationFailure.left()

        val result = given()
            .contentType(ContentType.JSON)
            .body("""{"text": "$text", "inBatch": false}""")
            .`when`()
            .put("/text-bits/$uuid")
            .then()
            .statusCode(200)
            .extract()
            .`as`(MessageResponse::class.java)

        result.message shouldBe "Text bit $uuid successfully replaced! But embedding creation failed!"
        val textBit = testTextBitsRepo.findById(uuid)
        textBit.shouldNotBeNull()
        textBit.embeddingStatus shouldBe TextBitEmbeddingStatus.UNEMBEDDED.name
        textBit.text shouldBe text
        textBit.game shouldBe Game.MAUSRITTER.name
        textBit.createdOn shouldBe creationTime
        textBit.updatedOn shouldBe now
        val persistedEmbedding = testEmbeddingsRepo.findByTextBitId(uuid)
        persistedEmbedding.shouldBeNull()
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

}
