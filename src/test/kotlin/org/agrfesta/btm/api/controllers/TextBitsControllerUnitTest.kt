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
import org.agrfesta.btm.api.model.TextBit
import org.agrfesta.btm.api.model.TextBitEmbeddingStatus.EMBEDDED
import org.agrfesta.btm.api.model.Topic
import org.agrfesta.btm.api.persistence.TextBitsDao
import org.agrfesta.btm.api.persistence.EmbeddingsDao
import org.agrfesta.btm.api.services.EmbeddingsService
import org.agrfesta.btm.api.services.TextBitsService
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

@WebMvcTest(TextBitsController::class)
@Import(TextBitsService::class)
@ActiveProfiles("test")
class TextBitsControllerUnitTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired @MockkBean private val textBitsDao: TextBitsDao,
    @Autowired @MockkBean private val embeddingsDao: EmbeddingsDao,
    @Autowired @MockkBean private val embeddingsService: EmbeddingsService
) {
    private val game = Game.MAUSRITTER
    private val topic = Topic.RULE
    private val uuid = UUID.randomUUID()
    private val text = aRandomUniqueString()

    ///// createTextBit ////////////////////////////////////////////////////////////////////////////////////////////////

    @Test fun `createTextBit() Returns 500 when creation fails`() {
        every { textBitsDao.persist(Game.MAUSRITTER, text, topic) } returns
                PersistenceFailure("creation failure").left()
        val responseBody: String = mockMvc.perform(
            post("/text-bits")
                .contentType("application/json")
                .content("""{"game": "MAUSRITTER", "text": "$text", "topic": "$topic", "inBatch": false}"""))
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        coVerify(exactly = 0) { embeddingsService.createEmbedding(any()) }
        verify(exactly = 0) { embeddingsDao.persist(any(), any(), any(), any()) }
        verify(exactly = 0) { textBitsDao.update(any(), any()) }
        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Unable to create text bit!"
    }

    @Test fun `createTextBit() Returns 200 when creates text bit but fails to persist embedding`() {
        every { textBitsDao.persist(Game.MAUSRITTER, text, topic) } returns uuid.right()
        val embedding = anEmbedding()
        coEvery { embeddingsService.createEmbedding(text) } returns embedding.right()
        every { embeddingsDao.persist(any(), any(), any(), any()) } returns
                PersistenceFailure("embedding persistence failure").left()
        val responseBody: String = mockMvc.perform(
            post("/text-bits")
                .contentType("application/json")
                .content("""{"game": "MAUSRITTER", "text": "$text", "topic": "$topic", "inBatch": false}"""))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        verify(exactly = 0) { textBitsDao.update(any(), any()) }
        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Text bit $uuid successfully persisted! But embedding creation failed!"
    }

    @Test fun `createTextBit() Returns 200 when creates text bit and persist embedding but fails updating text bit`() {
        every { textBitsDao.persist(Game.MAUSRITTER, text, topic) } returns uuid.right()
        val embedding = anEmbedding()
        coEvery { embeddingsService.createEmbedding(text) } returns embedding.right()
        every { embeddingsDao.persist(any(), any(), any(), any()) } returns UUID.randomUUID().right()
        every { textBitsDao.update(uuid, EMBEDDED, null) } returns
                PersistenceFailure("text bit update failure").left()
        val responseBody: String = mockMvc.perform(
            post("/text-bits")
                .contentType("application/json")
                .content("""{"game": "MAUSRITTER", "text": "$text", "topic": "$topic", "inBatch": false}"""))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Text bit $uuid successfully persisted! But embedding creation failed!"
    }

    @TestFactory
    fun `createTextBit() returns 400 when text is empty`() = listOf("", " ", "  ", "    ").map {
        dynamicTest(" -> '$it'") {
            val responseBody: String = mockMvc.perform(
                post("/text-bits")
                    .contentType("application/json")
                    .content("""{"game": "MAUSRITTER", "text": "$it", "topic": "$topic", "inBatch": false}"""))
                .andExpect(status().isBadRequest)
                .andReturn().response.contentAsString

            verify(exactly = 0) { textBitsDao.persist(any(), any(), any()) }
            coVerify(exactly = 0) { embeddingsService.createEmbedding(any()) }
            verify(exactly = 0) { embeddingsDao.persist(any(), any(), any(), any()) }
            verify(exactly = 0) { textBitsDao.update(any(), any()) }
            val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
            response.message shouldBe "Text must not be empty!"
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    ///// replaceTextBits //////////////////////////////////////////////////////////////////////////////////////////////

    @TestFactory
    fun `replaceTextBits() returns 400 when text is empty`() = listOf("", " ", "  ", "    ").map {
        dynamicTest(" -> '$it'") {
            val responseBody: String = mockMvc.perform(
                put("/text-bits/${UUID.randomUUID()}")
                    .contentType("application/json")
                    .content("""{"text": "$it", "inBatch": false}"""))
                .andExpect(status().isBadRequest)
                .andReturn().response.contentAsString

            verify(exactly = 0) { textBitsDao.persist(any(), any(), any()) }
            coVerify(exactly = 0) { embeddingsService.createEmbedding(any()) }
            verify(exactly = 0) { embeddingsDao.persist(any(), any(), any(), any()) }
            verify(exactly = 0) { textBitsDao.update(any(), any()) }
            val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
            response.message shouldBe "Text must not be empty!"
        }
    }

    @Test fun `replaceTextBits() Returns 500 when fails to fetch text bit`() {
        every { textBitsDao.findTextBit(uuid) } throws Exception("text bit fetch failure")
        val responseBody: String = mockMvc.perform(
            put("/text-bits/$uuid")
                .contentType("application/json")
                .content("""{"text": "$text", "inBatch": false}"""))
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        coVerify(exactly = 0) { embeddingsService.createEmbedding(any()) }
        verify(exactly = 0) { embeddingsDao.persist(any(), any(), any(), any()) }
        verify(exactly = 0) { textBitsDao.update(any(), any()) }
        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Unable to replace text bit $uuid!"
    }

    @Test fun `replaceTextBits() Returns 500 when embedding removal fails`() {
        every { textBitsDao.findTextBit(uuid) } returns TextBit(uuid, game, text)
        every { embeddingsDao.deleteByTextBitId(uuid) } returns
                PersistenceFailure("embedding removal failure").left()
        val responseBody: String = mockMvc.perform(
            put("/text-bits/$uuid")
                .contentType("application/json")
                .content("""{"text": "$text", "inBatch": false}"""))
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        coVerify(exactly = 0) { embeddingsService.createEmbedding(any()) }
        verify(exactly = 0) { embeddingsDao.persist(any(), any(), any(), any()) }
        verify(exactly = 0) { textBitsDao.update(any(), any()) }
        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Unable to replace text bit $uuid!"
    }

    @Test fun `replaceTextBits() Returns 500 when text replacement fails`() {
        every { textBitsDao.findTextBit(uuid) } returns TextBit(uuid, game, text)
        every { embeddingsDao.deleteByTextBitId(uuid) } returns Unit.right()
        every { textBitsDao.replaceText(uuid, text) } returns
                PersistenceFailure("text replacement failure").left()
        val responseBody: String = mockMvc.perform(
            put("/text-bits/$uuid")
                .contentType("application/json")
                .content("""{"text": "$text", "inBatch": false}"""))
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        coVerify(exactly = 0) { embeddingsService.createEmbedding(any()) }
        verify(exactly = 0) { embeddingsDao.persist(any(), any(), any(), any()) }
        verify(exactly = 0) { textBitsDao.update(any(), any()) }
        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Unable to replace text bit $uuid!"
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

}
