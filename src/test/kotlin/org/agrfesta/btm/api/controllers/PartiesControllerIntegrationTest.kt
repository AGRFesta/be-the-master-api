package org.agrfesta.btm.api.controllers

import com.ninjasquad.springmockk.MockkBean
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.restassured.RestAssured
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.agrfesta.btm.api.model.Game
import org.agrfesta.btm.api.persistence.jdbc.repositories.PartiesRepository
import org.agrfesta.btm.api.services.utils.RandomGenerator
import org.agrfesta.btm.api.services.utils.TimeService
import org.agrfesta.btm.api.services.utils.toNoNanoSec
import org.agrfesta.test.mothers.aRandomUniqueString
import org.agrfesta.test.mothers.aSheet
import org.junit.jupiter.api.BeforeEach
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class PartiesControllerIntegrationTest(
    @Autowired private val partiesRepository: PartiesRepository,
    @Autowired @MockkBean private val randomGenerator: RandomGenerator,
    @Autowired @MockkBean private val timeService: TimeService
) {
    private val uuid: UUID = UUID.randomUUID()
    private val now = Instant.now().toNoNanoSec()

    companion object {
        @Container
        @ServiceConnection
        val postgres: PostgreSQLContainer<*> = DockerImageName.parse("pgvector/pgvector:pg16")
            .asCompatibleSubstituteFor("postgres")
            .let { PostgreSQLContainer(it) }
    }

    @LocalServerPort private val port: Int? = null

    @BeforeEach
    fun setUp() {
        RestAssured.baseURI = "http://localhost:$port"

        every { randomGenerator.uuid() } returns uuid
        every { timeService.nowNoNano() } returns now
    }

    @Test
    fun `createParty() Returns 201 when creates party`() {
        val name = aRandomUniqueString()
        val result = given()
            .contentType(ContentType.JSON)
            .body("""{"name": "$name", "game": "MAUSRITTER", "sheets": [${aSheet()},${aSheet()}]}""")
            .`when`()
            .post("/parties")
            .then()
            .statusCode(201)
            .extract()
            .`as`(MessageResponse::class.java)

        result.message shouldBe "Party $uuid successfully persisted!"
        val party = partiesRepository.findPartyWithMembers(uuid)
        party.shouldNotBeNull()
        party.name shouldBe name
        party.game shouldBe Game.MAUSRITTER
        party.members shouldHaveSize 2
    }

}
