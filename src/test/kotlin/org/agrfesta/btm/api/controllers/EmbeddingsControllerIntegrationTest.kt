package org.agrfesta.btm.api.controllers

import arrow.core.right
import com.ninjasquad.springmockk.MockkBean
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.restassured.RestAssured
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.agrfesta.btm.api.model.Game
import org.agrfesta.btm.api.persistence.TestingRulesEmbeddingRepository
import org.agrfesta.btm.api.services.EmbeddingsService
import org.agrfesta.btm.api.services.utils.RandomGenerator
import org.agrfesta.btm.api.services.utils.TimeService
import org.agrfesta.btm.api.services.utils.toNoNanoSec
import org.agrfesta.test.mothers.aRandomUniqueString
import org.agrfesta.test.mothers.anEmbedding
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
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

@Disabled("the controller will be removed soon")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class EmbeddingsControllerIntegrationTest(
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

    @LocalServerPort private val port: Int? = null

    private val uuid: UUID = UUID.randomUUID()
    private val now = Instant.now().toNoNanoSec()

    @BeforeEach
    fun setUp() {
        RestAssured.baseURI = "http://localhost:$port"

        every { randomGenerator.uuid() } returns uuid
        every { timeService.nowNoNano() } returns now
    }

    @Test
    fun `create() return 200 when successfully create rule embedding`() {
        val text = aRandomUniqueString()
        val embedding = anEmbedding()
        coEvery { embeddingsService.createEmbedding(text) } returns embedding.right()

        val result = given()
            .contentType(ContentType.JSON)
            .body("""{"game": "MAUSRITTER", "text": "$text"}""")
            .`when`()
            .post("/embeddings/rules")
            .then()
            .statusCode(200)
            .extract()
            .`as`(MessageResponse::class.java)

        result.message shouldBe "Embedding $uuid successfully persisted!"
        val ruleEmbedding = testRulesEmbeddingsRepo.findById(uuid)
        ruleEmbedding.shouldNotBeNull()
        ruleEmbedding.text shouldBe text
        ruleEmbedding.game shouldBe Game.MAUSRITTER.name
        ruleEmbedding.createdOn shouldBe now
        ruleEmbedding.vector shouldBe embedding
    }

}
