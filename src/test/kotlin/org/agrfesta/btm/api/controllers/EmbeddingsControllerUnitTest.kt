package org.agrfesta.btm.api.controllers

import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.verify
import org.agrfesta.btm.api.model.EmbeddingCreationFailure
import org.agrfesta.btm.api.model.Game
import org.agrfesta.btm.api.persistence.PersistenceFailure
import org.agrfesta.btm.api.persistence.RulesEmbeddingsDao
import org.agrfesta.btm.api.services.EmbeddingsService
import org.agrfesta.btm.api.services.utils.RandomGenerator
import org.agrfesta.btm.api.services.utils.TimeService
import org.agrfesta.test.mothers.aRandomUniqueString
import org.agrfesta.test.mothers.anEmbedding
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(EmbeddingsController::class)
@ActiveProfiles("test")
class EmbeddingsControllerUnitTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired @MockkBean private val embeddingsService: EmbeddingsService,
    @Autowired @MockkBean private val rulesEmbeddingsDao: RulesEmbeddingsDao,
    @Autowired @MockkBean private val randomGenerator: RandomGenerator,
    @Autowired @MockkBean private val timeService: TimeService
) {

    @TestFactory
    fun `createRuleEmbedding() returns 400 when text is empty`() = listOf("", " ", "  ", "    ").map {
        dynamicTest(" -> '$it'") {
            val responseBody: String = mockMvc.perform(
                post("/embeddings/rules")
                    .contentType("application/json")
                    .content("""{"game": "MAUSRITTER", "text": "$it"}"""))
                .andExpect(status().isBadRequest)
                .andReturn().response.contentAsString

            coVerify(exactly = 0) { embeddingsService.createEmbedding(any()) }
            verify(exactly = 0) { rulesEmbeddingsDao.persist(any(), any(), any()) }
            val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
            response.message shouldBe "Text must not be empty!"
        }
    }

    @Test
    fun `createRuleEmbedding() returns 500 when embedding creation fails`() {
        val text = aRandomUniqueString()
        coEvery { embeddingsService.createEmbedding(text) } returns EmbeddingCreationFailure.left()
        val responseBody: String = mockMvc.perform(
            post("/embeddings/rules")
                .contentType("application/json")
                .content("""{"game": "MAUSRITTER", "text": "$text"}"""))
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        verify(exactly = 0) { rulesEmbeddingsDao.persist(any(), any(), any()) }
        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Unable to create embedding!"
    }

    @Test
    fun `createRuleEmbedding() returns 500 when fails to persist embedding`() {
        val text = aRandomUniqueString()
        val embedding = anEmbedding()
        coEvery { embeddingsService.createEmbedding(text) } returns embedding.right()
        every { rulesEmbeddingsDao.persist(Game.MAUSRITTER, embedding, text) } returns PersistenceFailure.left()
        val responseBody: String = mockMvc.perform(
            post("/embeddings/rules")
                .contentType("application/json")
                .content("""{"game": "MAUSRITTER", "text": "$text"}"""))
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Unable to persist embedding!"
    }

}
