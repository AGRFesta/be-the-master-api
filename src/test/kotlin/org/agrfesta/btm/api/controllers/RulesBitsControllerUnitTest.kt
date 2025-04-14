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
import org.agrfesta.btm.api.model.Game
import org.agrfesta.btm.api.model.PersistenceFailure
import org.agrfesta.btm.api.model.RuleBit
import org.agrfesta.btm.api.model.RuleBitsEmbeddingStatus.EMBEDDED
import org.agrfesta.btm.api.persistence.RulesBitsDao
import org.agrfesta.btm.api.persistence.RulesEmbeddingsDao
import org.agrfesta.btm.api.services.EmbeddingsService
import org.agrfesta.btm.api.services.RulesService
import org.agrfesta.test.mothers.aRandomUniqueString
import org.agrfesta.test.mothers.anEmbedding
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.*

@WebMvcTest(RulesBitsController::class)
@Import(RulesService::class)
@ActiveProfiles("test")
class RulesBitsControllerUnitTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired @MockkBean private val rulesBitsDao: RulesBitsDao,
    @Autowired @MockkBean private val rulesEmbeddingsDao: RulesEmbeddingsDao,
    @Autowired @MockkBean private val embeddingsService: EmbeddingsService
) {
    private val game = Game.MAUSRITTER
    private val uuid = UUID.randomUUID()
    private val text = aRandomUniqueString()

    ///// createRuleBit ////////////////////////////////////////////////////////////////////////////////////////////////

    @Test fun `createRuleBit() Returns 500 when creation fails`() {
        every { rulesBitsDao.persist(Game.MAUSRITTER, text) } returns
                PersistenceFailure("creation failure").left()
        val responseBody: String = mockMvc.perform(
            post("/rules/bits")
                .contentType("application/json")
                .content("""{"game": "MAUSRITTER", "text": "$text", "inBatch": false}"""))
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        coVerify(exactly = 0) { embeddingsService.createEmbedding(any()) }
        verify(exactly = 0) { rulesEmbeddingsDao.persist(any(), any(), any(), any()) }
        verify(exactly = 0) { rulesBitsDao.update(any(), any()) }
        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Unable to create rule bit!"
    }

    @Test fun `createRuleBit() Returns 200 when creates rule bit but fails to persist embedding`() {
        every { rulesBitsDao.persist(Game.MAUSRITTER, text) } returns uuid.right()
        val embedding = anEmbedding()
        coEvery { embeddingsService.createEmbedding(text) } returns embedding.right()
        every { rulesEmbeddingsDao.persist(any(), any(), any(), any()) } returns
                PersistenceFailure("embedding persistence failure").left()
        val responseBody: String = mockMvc.perform(
            post("/rules/bits")
                .contentType("application/json")
                .content("""{"game": "MAUSRITTER", "text": "$text", "inBatch": false}"""))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        verify(exactly = 0) { rulesBitsDao.update(any(), any()) }
        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Rule bit $uuid successfully persisted! But embedding creation failed!"
    }

    @Test fun `createRuleBit() Returns 200 when creates rule bit and persist embedding but fails updating rule bit`() {
        every { rulesBitsDao.persist(Game.MAUSRITTER, text) } returns uuid.right()
        val embedding = anEmbedding()
        coEvery { embeddingsService.createEmbedding(text) } returns embedding.right()
        every { rulesEmbeddingsDao.persist(any(), any(), any(), any()) } returns UUID.randomUUID().right()
        every { rulesBitsDao.update(uuid, EMBEDDED, null) } returns
                PersistenceFailure("rule bit update failure").left()
        val responseBody: String = mockMvc.perform(
            post("/rules/bits")
                .contentType("application/json")
                .content("""{"game": "MAUSRITTER", "text": "$text", "inBatch": false}"""))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Rule bit $uuid successfully persisted! But embedding creation failed!"
    }

    @TestFactory
    fun `createRuleBit() returns 400 when text is empty`() = listOf("", " ", "  ", "    ").map {
        dynamicTest(" -> '$it'") {
            val responseBody: String = mockMvc.perform(
                post("/rules/bits")
                    .contentType("application/json")
                    .content("""{"game": "MAUSRITTER", "text": "$it", "inBatch": false}"""))
                .andExpect(status().isBadRequest)
                .andReturn().response.contentAsString

            verify(exactly = 0) { rulesBitsDao.persist(any(), any()) }
            coVerify(exactly = 0) { embeddingsService.createEmbedding(any()) }
            verify(exactly = 0) { rulesEmbeddingsDao.persist(any(), any(), any(), any()) }
            verify(exactly = 0) { rulesBitsDao.update(any(), any()) }
            val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
            response.message shouldBe "Text must not be empty!"
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    ///// replaceRuleBits //////////////////////////////////////////////////////////////////////////////////////////////

    @TestFactory
    fun `replaceRuleBits() returns 400 when text is empty`() = listOf("", " ", "  ", "    ").map {
        dynamicTest(" -> '$it'") {
            val responseBody: String = mockMvc.perform(
                put("/rules/bits/${UUID.randomUUID()}")
                    .contentType("application/json")
                    .content("""{"text": "$it", "inBatch": false}"""))
                .andExpect(status().isBadRequest)
                .andReturn().response.contentAsString

            verify(exactly = 0) { rulesBitsDao.persist(any(), any()) }
            coVerify(exactly = 0) { embeddingsService.createEmbedding(any()) }
            verify(exactly = 0) { rulesEmbeddingsDao.persist(any(), any(), any(), any()) }
            verify(exactly = 0) { rulesBitsDao.update(any(), any()) }
            val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
            response.message shouldBe "Text must not be empty!"
        }
    }

    @Test fun `replaceRuleBits() Returns 500 when fails to fetch rule bit`() {
        every { rulesBitsDao.findRuleBit(uuid) } throws Exception("rule bit fetch failure")
        val responseBody: String = mockMvc.perform(
            put("/rules/bits/$uuid")
                .contentType("application/json")
                .content("""{"text": "$text", "inBatch": false}"""))
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        coVerify(exactly = 0) { embeddingsService.createEmbedding(any()) }
        verify(exactly = 0) { rulesEmbeddingsDao.persist(any(), any(), any(), any()) }
        verify(exactly = 0) { rulesBitsDao.update(any(), any()) }
        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Unable to replace rule bit $uuid!"
    }

    @Test fun `replaceRuleBits() Returns 500 when embedding removal fails`() {
        every { rulesBitsDao.findRuleBit(uuid) } returns RuleBit(uuid, game, text)
        every { rulesEmbeddingsDao.deleteByRuleId(uuid) } returns
                PersistenceFailure("embedding removal failure").left()
        val responseBody: String = mockMvc.perform(
            put("/rules/bits/$uuid")
                .contentType("application/json")
                .content("""{"text": "$text", "inBatch": false}"""))
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        coVerify(exactly = 0) { embeddingsService.createEmbedding(any()) }
        verify(exactly = 0) { rulesEmbeddingsDao.persist(any(), any(), any(), any()) }
        verify(exactly = 0) { rulesBitsDao.update(any(), any()) }
        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Unable to replace rule bit $uuid!"
    }

    @Test fun `replaceRuleBits() Returns 500 when text replacement fails`() {
        every { rulesBitsDao.findRuleBit(uuid) } returns RuleBit(uuid, game, text)
        every { rulesEmbeddingsDao.deleteByRuleId(uuid) } returns Unit.right()
        every { rulesBitsDao.replaceText(uuid, text) } returns
                PersistenceFailure("text replacement failure").left()
        val responseBody: String = mockMvc.perform(
            put("/rules/bits/$uuid")
                .contentType("application/json")
                .content("""{"text": "$text", "inBatch": false}"""))
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        coVerify(exactly = 0) { embeddingsService.createEmbedding(any()) }
        verify(exactly = 0) { rulesEmbeddingsDao.persist(any(), any(), any(), any()) }
        verify(exactly = 0) { rulesBitsDao.update(any(), any()) }
        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Unable to replace rule bit $uuid!"
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

}
