package org.agrfesta.btm.api.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.restassured.RestAssured
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.agrfesta.btm.api.model.Game.MAUSRITTER
import org.agrfesta.btm.api.persistence.jdbc.repositories.GlossariesRepository
import org.agrfesta.btm.api.services.utils.TimeService
import org.agrfesta.btm.api.services.utils.toNoNanoSec
import org.agrfesta.test.mothers.aRandomUniqueString
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class GlossariesControllerIntegrationTest(
    @Autowired private val glossariesRepository: GlossariesRepository,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired @MockkBean private val timeService: TimeService
) {
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

        every { timeService.nowNoNano() } returns now
    }

    @Test
    fun `addEntries() Returns 200 when adds entries`() {
        glossariesRepository.deleteEntriesByGame(MAUSRITTER)
        val entryA = aRandomUniqueString() to aRandomUniqueString()
        val entryC = aRandomUniqueString() to aRandomUniqueString()
        val entryB = aRandomUniqueString() to aRandomUniqueString()
        val entries = listOf(entryA, entryB, entryC).toMap()

        val result = given()
            .contentType(ContentType.JSON)
            .body(objectMapper.writeValueAsString(entries))
            .`when`()
            .post("/glossaries/${MAUSRITTER}/entries")
            .then()
            .statusCode(200)
            .extract().body().asString()

        val response: AddGlossaryItemsResponse = objectMapper.readValue(result, AddGlossaryItemsResponse::class.java)
        response.entriesAdded shouldBe 3
        response.duplicates.shouldBeEmpty()
        val persistedEntries = glossariesRepository.getAllEntriesByGame(MAUSRITTER)
        persistedEntries.shouldContainExactly(entries)
    }

    @Test
    fun `addEntries() Returns 200 when some entries only and the others are duplicates`() {
        glossariesRepository.deleteEntriesByGame(MAUSRITTER)
        val entryA = aRandomUniqueString() to aRandomUniqueString()
        val entryC = aRandomUniqueString() to aRandomUniqueString()
        val entryB = aRandomUniqueString() to aRandomUniqueString()
        val entries = listOf(entryA, entryB, entryC).toMap()
        glossariesRepository.insertEntry(MAUSRITTER, entryA.first, entryA.second, now)
        glossariesRepository.insertEntry(MAUSRITTER, entryC.first, entryC.second, now)

        val result = given()
            .contentType(ContentType.JSON)
            .body(objectMapper.writeValueAsString(entries))
            .`when`()
            .post("/glossaries/${MAUSRITTER}/entries")
            .then()
            .statusCode(200)
            .extract().body().asString()

        val response: AddGlossaryItemsResponse = objectMapper.readValue(result, AddGlossaryItemsResponse::class.java)
        response.entriesAdded shouldBe 1
        response.duplicates.shouldContainExactly(listOf(entryA, entryC).toMap())
        val persistedEntries = glossariesRepository.getAllEntriesByGame(MAUSRITTER)
        persistedEntries.shouldContainExactly(entries)
    }

}
