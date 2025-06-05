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
import org.agrfesta.btm.api.model.EmbeddingStatus.EMBEDDED
import org.agrfesta.btm.api.model.Game
import org.agrfesta.btm.api.model.PersistenceFailure
import org.agrfesta.btm.api.model.Topic
import org.agrfesta.btm.api.persistence.EmbeddingsDao
import org.agrfesta.btm.api.persistence.ChunksDao
import org.agrfesta.btm.api.persistence.TranslationsDao
import org.agrfesta.btm.api.services.EmbeddingsProvider
import org.agrfesta.btm.api.services.ChunksService
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

@WebMvcTest(ChunksController::class)
@Import(ChunksService::class, ChunksUnitAsserter::class)
@ActiveProfiles("test")
class ChunksControllerUnitTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val asserter: ChunksUnitAsserter,
    @Autowired @MockkBean private val chunksDao: ChunksDao,
    @Autowired @MockkBean private val embeddingsDao: EmbeddingsDao,
    @Autowired @MockkBean private val translationsDao: TranslationsDao,
    @Autowired @MockkBean private val embeddingsProvider: EmbeddingsProvider
) {
    private val game = Game.MAUSRITTER
    private val topic = Topic.RULE
    private val uuid = UUID.randomUUID()

    ///// createChunk ////////////////////////////////////////////////////////////////////////////////////////////////

    @Test fun `createChunk() Returns 500 when creation fails`() {
        every { chunksDao.persist(topic, game) } throws Exception("creation failure")
        val responseBody: String = mockMvc.perform(
            post("/chunks")
                .contentType("application/json")
                .content(aChunkCreationRequest(topic = topic, game = game).toJsonString()))
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        coVerify(exactly = 0) { embeddingsProvider.createEmbedding(any()) }
        asserter.verifyNoTranslationsPersisted()
        asserter.verifyNoEmbeddingsPersisted()
        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Unable to create text bit!"
    }

    @Test fun `createChunk() Creates text bit and translations only, when fails to persist embedding`() {
        val translation = aTranslation(language = "en")
        val request = aChunkCreationRequest(
            topic = topic, game = game,
            translation = translation
        )
        val translationEmbedding = anEmbedding()
        val translationId = UUID.randomUUID()
        every { chunksDao.persist(topic, game) } returns uuid
        every { translationsDao.persist(uuid, translation) } returns translationId
        coEvery { embeddingsProvider.createEmbedding(translation.text) } returns translationEmbedding.right()
        every { embeddingsDao.persist(translationId, translationEmbedding) } returns
                PersistenceFailure("embedding persistence failure").left()

        val responseBody: String = mockMvc.perform(
            post("/chunks")
                .contentType("application/json")
                .content(request.toJsonString()))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe
                "Text bit successfully persisted! Failed embeddings creation."
        verify(exactly = 0) { translationsDao.setEmbeddingStatus(translationId, EMBEDDED) }
    }

    // TODO what happen when setEmbeddingStatus fails? (Should rollback embedding creation) or (just remove that column)

    @TestFactory
    fun `createChunk() returns 400 when original text is empty`() = listOf("", " ", "  ", "    ").map {
        dynamicTest(" -> '$it'") {
            val responseBody: String = mockMvc.perform(
                post("/chunks")
                    .contentType("application/json")
                    .content(aChunkCreationRequest(translation = aTranslation(text = it)).toJsonString()))
                .andExpect(status().isBadRequest)
                .andReturn().response.contentAsString

            verify(exactly = 0) { chunksDao.persist(any(), any()) }
            coVerify(exactly = 0) { embeddingsProvider.createEmbedding(any()) }
            verify(exactly = 0) { embeddingsDao.persist(any(), any()) }
            verify(exactly = 0) { translationsDao.setEmbeddingStatus(any(), any()) }
            val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
            response.message shouldBe "Text must not be empty!"
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    ///// update ///////////////////////////////////////////////////////////////////////////////////////////////////////

    @TestFactory
    fun `update() returns 400 when new text is empty`() = listOf("", " ", "  ", "    ").map {
        dynamicTest(" -> '$it'") {
            val request = aChunkTranslationsPatchRequest(text = it, language = "en")
            val responseBody: String = mockMvc.perform(
                patch("/chunks/${UUID.randomUUID()}")
                    .contentType("application/json")
                    .content(request.toJsonString()))
                .andExpect(status().isBadRequest)
                .andReturn().response.contentAsString

            verify(exactly = 0) { chunksDao.persist(any(), any()) }
            coVerify(exactly = 0) { embeddingsProvider.createEmbedding(any()) }
            verify(exactly = 0) { embeddingsDao.persist(any(), any()) }
            verify(exactly = 0) { translationsDao.setEmbeddingStatus(any(), any()) }
            val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
            response.message shouldBe "Text must not be empty!"
        }
    }

    @Test fun `update() Returns 500 when fails to fetch text bit`() {
        val request = aChunkTranslationsPatchRequest()
        every { chunksDao.findChunk(uuid) } throws Exception("text bit fetch failure")
        val responseBody: String = mockMvc.perform(
            patch("/chunks/$uuid")
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
        every { chunksDao.findChunk(uuid) } returns aChunk(id = uuid)
        val request = aChunkTranslationsPatchRequest(inBatch = false)
        every { translationsDao.addOrReplace(uuid, request.language, request.text) }throws
                Exception("embedding removal failure")

        val responseBody: String = mockMvc.perform(
            patch("/chunks/$uuid")
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

    ///// similaritySearch /////////////////////////////////////////////////////////////////////////////////////////////

    @TestFactory
    fun `similaritySearch() returns 400 when text is blank`() = listOf("", " ", "  ", "    ").map {
        dynamicTest(" -> '$it'") {
            val request = aChunkSearchBySimilarityRequest(text = it)
            val responseBody: String = mockMvc.perform(
                post("/chunks/similarity-search")
                    .contentType("application/json")
                    .content(request.toJsonString()))
                .andExpect(status().isBadRequest)
                .andReturn().response.contentAsString

            verify(exactly = 0) { embeddingsDao.searchBySimilarity(any(), any(), any(), any()) }
            val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
            response.message shouldBe "Text must not be blank!"
        }
    }

    @TestFactory
    fun `similaritySearch() returns 400 when language is not valid`() = listOf("", " ", "  ", "    ", "i", "ita").map {
        dynamicTest(" -> '$it'") {
            val request = aChunkSearchBySimilarityRequest(language = it)
            val responseBody: String = mockMvc.perform(
                post("/chunks/similarity-search")
                    .contentType("application/json")
                    .content(request.toJsonString()))
                .andExpect(status().isBadRequest)
                .andReturn().response.contentAsString

            verify(exactly = 0) { embeddingsDao.searchBySimilarity(any(), any(), any(), any()) }
            val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
            response.message shouldBe "Language must not be blank and two charters length!"
        }
    }

    @TestFactory
    fun `similaritySearch() returns 400 when Game is not valid`() =
        listOf("", " ", "  ", "    ", aRandomUniqueString()).map {
            dynamicTest(" -> '$it'") {
                val requestJson = aChunkSearchBySimilarityRequestJson(game = it)
                val responseBody: String = mockMvc.perform(
                    post("/chunks/similarity-search")
                        .contentType("application/json")
                        .content(requestJson))
                    .andExpect(status().isBadRequest)
                    .andReturn().response.contentAsString

                verify(exactly = 0) { embeddingsDao.searchBySimilarity(any(), any(), any(), any()) }
                val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
                response.message shouldBe "Game is not valid!"
            }
        }

    @TestFactory
    fun `similaritySearch() returns 400 when Topic is not valid`() =
        listOf("", " ", "  ", "    ", aRandomUniqueString()).map {
            dynamicTest(" -> '$it'") {
                val requestJson = aChunkSearchBySimilarityRequestJson(topic = it)
                val responseBody: String = mockMvc.perform(
                    post("/chunks/similarity-search")
                        .contentType("application/json")
                        .content(requestJson))
                    .andExpect(status().isBadRequest)
                    .andReturn().response.contentAsString

                verify(exactly = 0) { embeddingsDao.searchBySimilarity(any(), any(), any(), any()) }
                val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
                response.message shouldBe "Topic is not valid!"
            }
        }

    @Test fun `similaritySearch() Returns 500 when fails to create request text embedding`() {
        val request = aChunkSearchBySimilarityRequest()
        coEvery { embeddingsProvider.createEmbedding(request.text) } returns EmbeddingCreationFailure.left()

        val responseBody: String = mockMvc.perform(
            post("/chunks/similarity-search")
                .contentType("application/json")
                .content(request.toJsonString()))
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Unable to create target embedding!"
    }

    @Test fun `similaritySearch() Returns 500 when fails to fetch embeddings`() {
        val game = aGame()
        val topic = aTopic()
        val failure = Exception("embeddings fetch failure")
        val request = aChunkSearchBySimilarityRequest(game = game, topic = topic)
        val targetEmbedding = anEmbedding()
        coEvery { embeddingsProvider.createEmbedding(request.text) } returns targetEmbedding.right()
        every { embeddingsDao.searchBySimilarity(targetEmbedding, game, topic, request.language) } throws failure

        val responseBody: String = mockMvc.perform(
            post("/chunks/similarity-search")
                .contentType("application/json")
                .content(request.toJsonString()))
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Unable to fetch embeddings!"
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

}
