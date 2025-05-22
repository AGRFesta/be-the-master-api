package org.agrfesta.btm.api.controllers

import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.verify
import org.agrfesta.btm.api.model.EmbeddingStatus.EMBEDDED
import org.agrfesta.btm.api.model.Game
import org.agrfesta.btm.api.model.PersistenceFailure
import org.agrfesta.btm.api.model.Topic
import org.agrfesta.btm.api.persistence.EmbeddingsDao
import org.agrfesta.btm.api.persistence.TextBitsDao
import org.agrfesta.btm.api.persistence.TranslationsDao
import org.agrfesta.btm.api.services.EmbeddingsProvider
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.*

@WebMvcTest(TextBitsController::class)
@Import(TextBitsService::class, TextBitsUnitAsserter::class)
@ActiveProfiles("test")
class TextBitsControllerUnitTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val asserter: TextBitsUnitAsserter,
    @Autowired @MockkBean private val textBitsDao: TextBitsDao,
    @Autowired @MockkBean private val embeddingsDao: EmbeddingsDao,
    @Autowired @MockkBean private val translationsDao: TranslationsDao,
    @Autowired @MockkBean private val embeddingsProvider: EmbeddingsProvider
) {
    private val game = Game.MAUSRITTER
    private val topic = Topic.RULE
    private val uuid = UUID.randomUUID()

    ///// createTextBit ////////////////////////////////////////////////////////////////////////////////////////////////

    @Test fun `createTextBit() Returns 500 when creation fails`() {
        every { textBitsDao.persist(topic, game) } throws Exception("creation failure")
        val responseBody: String = mockMvc.perform(
            post("/text-bits")
                .contentType("application/json")
                .content(aTextBitCreationRequest(topic = topic, game = game).toJsonString()))
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        coVerify(exactly = 0) { embeddingsProvider.createEmbedding(any()) }
        asserter.verifyNoTranslationsPersisted()
        asserter.verifyNoEmbeddingsPersisted()
        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Unable to create text bit!"
    }

    @Test fun `createTextBit() Creates text bit and translations only, when fails to persist embedding`() {
        val original = aTranslation(language = "en")
        val itTranslation = aTranslation(language = "it")
        val frTranslation = aTranslation(language = "fr")
        val request = aTextBitCreationRequest(
            topic = topic, game = game,
            originalText = original,
            translations = listOf(itTranslation, frTranslation)
        )
        val originalEmbedding = anEmbedding()
        val itEmbedding = anEmbedding()
        val frEmbedding = anEmbedding()
        val originalId = UUID.randomUUID()
        val itTranslationId = UUID.randomUUID()
        val frTranslationId = UUID.randomUUID()
        every { textBitsDao.persist(topic, game) } returns uuid
        every { translationsDao.persist(uuid, original, true) } returns originalId
        every { translationsDao.persist(uuid, itTranslation, false) } returns itTranslationId
        every { translationsDao.persist(uuid, frTranslation, false) } returns frTranslationId
        coEvery { embeddingsProvider.createEmbedding(original.text) } returns originalEmbedding.right()
        coEvery { embeddingsProvider.createEmbedding(itTranslation.text) } returns itEmbedding.right()
        coEvery { embeddingsProvider.createEmbedding(frTranslation.text) } returns frEmbedding.right()
        every { embeddingsDao.persist(originalId, originalEmbedding) } returns
                PersistenceFailure("embedding persistence failure").left()
        every { embeddingsDao.persist(itTranslationId, itEmbedding) } returns UUID.randomUUID().right()
        every { embeddingsDao.persist(frTranslationId, frEmbedding) } returns
                PersistenceFailure("embedding persistence failure").left()
        every { translationsDao.setEmbeddingStatus(itTranslationId, EMBEDDED) } returns Unit

        val responseBody: String = mockMvc.perform(
            post("/text-bits")
                .contentType("application/json")
                .content(request.toJsonString()))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe
                "Text bit successfully persisted! Failed embeddings creation for languages [en, fr]"
        verify(exactly = 0) { translationsDao.setEmbeddingStatus(originalId, EMBEDDED) }
        verify(exactly = 0) { translationsDao.setEmbeddingStatus(frTranslationId, EMBEDDED) }
    }

    // TODO what happen when setEmbeddingStatus fails? (Should rollback embedding creation) or (just remove that column)

    @TestFactory
    fun `createTextBit() returns 400 when original text is empty`() = listOf("", " ", "  ", "    ").map {
        dynamicTest(" -> '$it'") {
            val responseBody: String = mockMvc.perform(
                post("/text-bits")
                    .contentType("application/json")
                    .content(aTextBitCreationRequest(originalText = aTranslation(text = it)).toJsonString()))
                .andExpect(status().isBadRequest)
                .andReturn().response.contentAsString

            verify(exactly = 0) { textBitsDao.persist(any(), any()) }
            coVerify(exactly = 0) { embeddingsProvider.createEmbedding(any()) }
            verify(exactly = 0) { embeddingsDao.persist(any(), any()) }
            verify(exactly = 0) { translationsDao.setEmbeddingStatus(any(), any()) }
            val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
            response.message shouldBe "Text must not be empty!"
        }
    }

    @Test fun `createTextBit() ignores translations with empty text`() {
        val request = aTextBitCreationRequest(
            inBatch = true,
            originalText = aTranslation(language = "en"),
            translations = listOf(
                aTranslation(text = "", language = "es"),
                aTranslation(text = " ", language = "it"),
                aTranslation(text = "  ", language = "fr"))
        )
        val textBit = request.toTextBit()
        asserter.givenTextBitCreation(textBit)

        val responseBody: String = mockMvc.perform(
            post("/text-bits")
                .contentType("application/json")
                .content(request.toJsonString()))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        asserter.verifyTranslationPersistence(textBit.original)
        confirmVerified(translationsDao) // no more interactions
        coVerify(exactly = 0) { embeddingsProvider.createEmbedding(any()) }
        asserter.verifyNoEmbeddingsPersisted()
        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Text bit successfully persisted!"
    }

    @Test fun `createTextBit() Returns 400 when there are multiple translations in the same language`() {
        val responseBody: String = mockMvc.perform(
            post("/text-bits")
                .contentType("application/json")
                .content(aTextBitCreationRequest(
                    originalText = aTranslation(language = "en"),
                    translations = listOf(aTranslation(language = "it"), aTranslation(language = "it"))
                ).toJsonString()))
            .andExpect(status().isBadRequest)
            .andReturn().response.contentAsString

        verify(exactly = 0) { textBitsDao.persist(any(), any()) }
        coVerify(exactly = 0) { embeddingsProvider.createEmbedding(any()) }
        verify(exactly = 0) { embeddingsDao.persist(any(), any()) }
        verify(exactly = 0) { translationsDao.setEmbeddingStatus(any(), any()) }
        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Multiple translations in the same language are not allowed"
    }

    @Test fun `createTextBit() Returns 400 when there are translations in the same language as the original one`() {
        val request = aTextBitCreationRequest(
            inBatch = true,
            originalText = aTranslation(language = "en"),
            translations = listOf(
                aTranslation(language = "en"),
                aTranslation(language = "it"))
        )

        val responseBody: String = mockMvc.perform(
            post("/text-bits")
                .contentType("application/json")
                .content(request.toJsonString()))
            .andExpect(status().isBadRequest)
            .andReturn().response.contentAsString

        asserter.verifyNothingPersisted()
        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Multiple translations in the same language are not allowed"
    }

    @Test fun `createTextBit() ignores translations with same language and text as original`() {
        val text = aRandomUniqueString()
        val itTranslation = aTranslation(language = "it")
        val request = aTextBitCreationRequest(
            inBatch = true,
            originalText = aTranslation(text = text, language = "en"),
            translations = listOf(
                aTranslation(text = text, language = "en"),
                itTranslation)
        )
        val textBit = request.toTextBit()
        asserter.givenTextBitCreation(textBit)

        val responseBody: String = mockMvc.perform(
            post("/text-bits")
                .contentType("application/json")
                .content(request.toJsonString()))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        asserter.verifyTranslationPersistence(textBit.original)
        asserter.verifyTranslationPersistence(itTranslation)
        confirmVerified(translationsDao) // no more interactions
        coVerify(exactly = 0) { embeddingsProvider.createEmbedding(any()) }
        asserter.verifyNoEmbeddingsPersisted()
        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Text bit successfully persisted!"
    }

    @Test fun `createTextBit() ignores translations with same language and text`() {
        val itTranslation = aTranslation(language = "it")
        val request = aTextBitCreationRequest(
            inBatch = true,
            originalText = aTranslation(language = "en"),
            translations = listOf(itTranslation, itTranslation)
        )
        val textBit = request.toTextBit()
        asserter.givenTextBitCreation(textBit)

        val responseBody: String = mockMvc.perform(
            post("/text-bits")
                .contentType("application/json")
                .content(request.toJsonString()))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        asserter.verifyTranslationPersistence(textBit.original)
        asserter.verifyTranslationPersistence(itTranslation)
        confirmVerified(translationsDao) // no more interactions
        coVerify(exactly = 0) { embeddingsProvider.createEmbedding(any()) }
        asserter.verifyNoEmbeddingsPersisted()
        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Text bit successfully persisted!"
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    ///// update ///////////////////////////////////////////////////////////////////////////////////////////////////////

    @TestFactory
    fun `update() returns 400 when new text is empty`() = listOf("", " ", "  ", "    ").map {
        dynamicTest(" -> '$it'") {
            val request = aTextBitTranslationsPatchRequest(text = it, language = "en")
            val responseBody: String = mockMvc.perform(
                patch("/text-bits/${UUID.randomUUID()}")
                    .contentType("application/json")
                    .content(request.toJsonString()))
                .andExpect(status().isBadRequest)
                .andReturn().response.contentAsString

            verify(exactly = 0) { textBitsDao.persist(any(), any()) }
            coVerify(exactly = 0) { embeddingsProvider.createEmbedding(any()) }
            verify(exactly = 0) { embeddingsDao.persist(any(), any()) }
            verify(exactly = 0) { translationsDao.setEmbeddingStatus(any(), any()) }
            val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
            response.message shouldBe "Text must not be empty!"
        }
    }

    @Test fun `update() Returns 500 when fails to fetch text bit`() {
        val request = aTextBitTranslationsPatchRequest()
        every { textBitsDao.findTextBit(uuid) } throws Exception("text bit fetch failure")
        val responseBody: String = mockMvc.perform(
            patch("/text-bits/$uuid")
                .contentType("application/json")
                .content(request.toJsonString()))
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        coVerify(exactly = 0) { embeddingsProvider.createEmbedding(any()) }
        verify(exactly = 0) { embeddingsDao.persist(any(), any()) }
        verify(exactly = 0) { translationsDao.setEmbeddingStatus(any(), any()) }
        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Unable to fetch text bit!"
    }

    @Test fun `update() Returns 500 when embedding removal fails`() {
        every { textBitsDao.findTextBit(uuid) } returns aTextBit(id = uuid)
        val request = aTextBitTranslationsPatchRequest(inBatch = false)
        every { translationsDao.addOrReplace(uuid, request.language, request.text) }throws
                Exception("embedding removal failure")

        val responseBody: String = mockMvc.perform(
            patch("/text-bits/$uuid")
                .contentType("application/json")
                .content(request.toJsonString()))
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        coVerify(exactly = 0) { embeddingsProvider.createEmbedding(any()) }
        verify(exactly = 0) { embeddingsDao.persist(any(), any()) }
        verify(exactly = 0) { translationsDao.setEmbeddingStatus(any(), any()) }
        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Unable to replace text bit $uuid!"
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

}
