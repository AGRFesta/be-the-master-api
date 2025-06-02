package org.agrfesta.btm.api.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
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
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.junit.jupiter.Container
import java.time.Instant

class GlossariesControllerIntegrationTest(
    @Autowired private val glossariesRepository: GlossariesRepository,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired @MockkBean private val timeService: TimeService
): AbstractIntegrationTest() {
    private val now = Instant.now().toNoNanoSec()

    companion object {
        @Container
        @ServiceConnection
        val postgres = createPostgresContainer()
    }

    @BeforeEach
    fun defaultMockBehaviourSetup() {
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
