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
import org.agrfesta.btm.api.model.PersistenceFailure
import org.agrfesta.btm.api.persistence.ChunksDao
import org.agrfesta.btm.api.persistence.EmbeddingsDao
import org.agrfesta.btm.api.persistence.TranslationsDao
import org.agrfesta.btm.api.services.ChunksService
import org.agrfesta.btm.api.services.EmbeddingsProvider
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
    private val game = aGame()
    private val topic = aTopic()
    private val language = aLanguage()
    private val uuid = UUID.randomUUID()

    ///// createChunks /////////////////////////////////////////////////////////////////////////////////////////////////

    @Test fun `createChunks() Returns 400 when texts is empty`() {
        val responseBody: String = mockMvc.perform(
            post("/chunks")
                .contentType("application/json")
                .content(aChunksCreationRequestJson(texts = listOf("", " ", "  "))))
            .andExpect(status().isBadRequest)
            .andReturn().response.contentAsString

        coVerify(exactly = 0) { embeddingsProvider.createEmbedding(any(), false) }
        asserter.verifyNoTranslationsPersisted()
        asserter.verifyNoEmbeddingsPersisted()
        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "No chunks to create!"
    }

    @TestFactory
    fun `createChunks() Returns 400 when language is not valid`() = listOf(null, "", " ", "  ", "i", "ita").map {
        dynamicTest(" -> '$it'") {
            val request = aChunksCreationRequestJson(language = it)
            val responseBody: String = mockMvc.perform(
                post("/chunks")
                    .contentType("application/json")
                    .content(request))
                .andExpect(status().isBadRequest)
                .andReturn().response.contentAsString

            coVerify(exactly = 0) { embeddingsProvider.createEmbedding(any(), false) }
            asserter.verifyNoTranslationsPersisted()
            asserter.verifyNoEmbeddingsPersisted()
            val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
            response.message shouldBe "Language must not be blank and two charters long!"
        }
    }

    @TestFactory
    fun `createChunks() Returns 400 when game is missing`() = listOf("", null).map {
            dynamicTest(" -> '$it'") {
                val requestJson = aChunksCreationRequestJson(game = it)
                val responseBody: String = mockMvc.perform(
                    post("/chunks")
                        .contentType("application/json")
                        .content(requestJson))
                    .andExpect(status().isBadRequest)
                    .andReturn().response.contentAsString

                coVerify(exactly = 0) { embeddingsProvider.createEmbedding(any(), false) }
                asserter.verifyNoTranslationsPersisted()
                asserter.verifyNoEmbeddingsPersisted()
                val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
                response.message shouldBe "Game is missing!"
            }
        }

    @Test fun `createChunks() Returns 400 when game is not valid`() {
        val requestJson = aChunksCreationRequestJson(game = aRandomUniqueString())
        val responseBody: String = mockMvc.perform(
            post("/chunks")
                .contentType("application/json")
                .content(requestJson))
            .andExpect(status().isBadRequest)
            .andReturn().response.contentAsString

        coVerify(exactly = 0) { embeddingsProvider.createEmbedding(any(), false) }
        asserter.verifyNoTranslationsPersisted()
        asserter.verifyNoEmbeddingsPersisted()
        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Game is not valid!"
    }

    @TestFactory
    fun `createChunks() Returns 400 when topic is missing`() = listOf("", null).map {
        dynamicTest(" -> '$it'") {
            val requestJson = aChunksCreationRequestJson(topic = it)
            val responseBody: String = mockMvc.perform(
                post("/chunks")
                    .contentType("application/json")
                    .content(requestJson))
                .andExpect(status().isBadRequest)
                .andReturn().response.contentAsString

            coVerify(exactly = 0) { embeddingsProvider.createEmbedding(any(), false) }
            asserter.verifyNoTranslationsPersisted()
            asserter.verifyNoEmbeddingsPersisted()
            val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
            response.message shouldBe "Topic is missing!"
        }
    }

    @Test fun `createChunks() Returns 400 when topic is not valid`() {
        val requestJson = aChunksCreationRequestJson(topic = aRandomUniqueString())
        val responseBody: String = mockMvc.perform(
            post("/chunks")
                .contentType("application/json")
                .content(requestJson))
            .andExpect(status().isBadRequest)
            .andReturn().response.contentAsString

        coVerify(exactly = 0) { embeddingsProvider.createEmbedding(any(), false) }
        asserter.verifyNoTranslationsPersisted()
        asserter.verifyNoEmbeddingsPersisted()
        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Topic is not valid!"
    }

    @Test fun `createChunks() Ignores failures persisting embeddings but mark chunk as not embedded`() {
        val cA = UUID.randomUUID()
        val cB = UUID.randomUUID()
        val cC = UUID.randomUUID()
        every { chunksDao.persist(topic, game) } returnsMany listOf(cA, cB, cC)
        val textA = aRandomUniqueString()
        val textB = aRandomUniqueString()
        val textC = aRandomUniqueString()
        val embA = anEmbedding()
        val embB = anEmbedding()
        val embC = anEmbedding()
        coEvery { embeddingsProvider.createEmbedding(textA, false) } returns embA.right()
        coEvery { embeddingsProvider.createEmbedding(textB, false) } returns embB.right()
        coEvery { embeddingsProvider.createEmbedding(textC, false) } returns embC.right()
        val tA = UUID.randomUUID()
        val tB = UUID.randomUUID()
        val tC = UUID.randomUUID()
        every { translationsDao.addOrReplace(cA, language, textA) } returns tA
        every { translationsDao.addOrReplace(cB, language, textB) } returns tB
        every { translationsDao.addOrReplace(cC, language, textC) } returns tC
        every { embeddingsDao.persist(tA, embA) } returns UUID.randomUUID().right()
        every { embeddingsDao.persist(tB, embB) } returns PersistenceFailure(aRandomUniqueString()).left()
        every { embeddingsDao.persist(tC, embC) } returns UUID.randomUUID().right()
        every { translationsDao.setEmbeddingStatus(tA, EMBEDDED) } returns Unit
        every { translationsDao.setEmbeddingStatus(tC, EMBEDDED) } returns Unit

        val responseBody: String = mockMvc.perform(
            post("/chunks")
                .contentType("application/json")
                .content(aChunksCreationRequestJson(
                    game = game.name,
                    topic = topic.name,
                    language = language,
                    texts = listOf(textA, textB, textC))))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        coVerify(exactly = 3) { embeddingsProvider.createEmbedding(any(), false) }
        verify(exactly = 3) { translationsDao.addOrReplace(any(), any(), any()) }
        verify(exactly = 2) { translationsDao.setEmbeddingStatus(any(), EMBEDDED) }
        verify { translationsDao.setEmbeddingStatus(tA, EMBEDDED) }
        verify { translationsDao.setEmbeddingStatus(tC, EMBEDDED) }
        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "3 Chunks successfully persisted!"
    }

    @Test fun `createChunks() Ignores failures persisting chunks`() {
        val textA = aRandomUniqueString()
        val textB = aRandomUniqueString()
        val textC = aRandomUniqueString()
        val cA = UUID.randomUUID()
        val cC = UUID.randomUUID()
        every { chunksDao.persist(topic, game) } returns cA andThenThrows Exception("failure") andThen cC
        val embA = anEmbedding()
        val embC = anEmbedding()
        coEvery { embeddingsProvider.createEmbedding(textA, false) } returns embA.right()
        coEvery { embeddingsProvider.createEmbedding(textC, false) } returns embC.right()
        val tA = UUID.randomUUID()
        val tC = UUID.randomUUID()
        every { translationsDao.addOrReplace(cA, language, textA) } returns tA
        every { translationsDao.addOrReplace(cC, language, textC) } returns tC
        every { embeddingsDao.persist(tA, embA) } returns UUID.randomUUID().right()
        every { embeddingsDao.persist(tC, embC) } returns UUID.randomUUID().right()
        every { translationsDao.setEmbeddingStatus(tA, EMBEDDED) } returns Unit
        every { translationsDao.setEmbeddingStatus(tC, EMBEDDED) } returns Unit

        val responseBody: String = mockMvc.perform(
            post("/chunks")
                .contentType("application/json")
                .content(aChunksCreationRequestJson(
                    game = game.name,
                    topic = topic.name,
                    language = language,
                    texts = listOf(textA, textB, textC))))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        coVerify(exactly = 2) { embeddingsProvider.createEmbedding(any(), false) }
        coVerify { embeddingsProvider.createEmbedding(textA, false) }
        coVerify { embeddingsProvider.createEmbedding(textC, false) }
        verify(exactly = 2) { translationsDao.addOrReplace(any(), any(), any()) }
        verify { translationsDao.addOrReplace(cA, language, textA) }
        verify { translationsDao.addOrReplace(cC, language, textC) }
        verify(exactly = 2) { translationsDao.setEmbeddingStatus(any(), EMBEDDED) }
        verify { translationsDao.setEmbeddingStatus(tA, EMBEDDED) }
        verify { translationsDao.setEmbeddingStatus(tC, EMBEDDED) }
        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "3 Chunks successfully persisted!"
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
            coVerify(exactly = 0) { embeddingsProvider.createEmbedding(any(), false) }
            verify(exactly = 0) { embeddingsDao.persist(any(), any()) }
            verify(exactly = 0) { translationsDao.setEmbeddingStatus(any(), any()) }
            val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
            response.message shouldBe "Text must not be empty!"
        }
    }

    @Test fun `update() Returns 500 when fails to fetch chunk`() {
        val request = aChunkTranslationsPatchRequest()
        every { chunksDao.findChunk(uuid) } throws Exception("chunk fetch failure")
        val responseBody: String = mockMvc.perform(
            patch("/chunks/$uuid")
                .contentType("application/json")
                .content(request.toJsonString()))
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        coVerify(exactly = 0) { embeddingsProvider.createEmbedding(any(), false) }
        verify(exactly = 0) { embeddingsDao.persist(any(), any()) }
        verify(exactly = 0) { translationsDao.setEmbeddingStatus(any(), any()) }
        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Unable to replace chunk $uuid!"
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

        coVerify(exactly = 0) { embeddingsProvider.createEmbedding(any(), false) }
        verify(exactly = 0) { embeddingsDao.persist(any(), any()) }
        verify(exactly = 0) { translationsDao.setEmbeddingStatus(any(), any()) }
        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Unable to replace chunk $uuid!"
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

            verify(exactly = 0) { embeddingsDao.searchBySimilarity(any(), any(), any(), any(), any(), any()) }
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

            verify(exactly = 0) { embeddingsDao.searchBySimilarity(any(), any(), any(), any(), any(), any()) }
            val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
            response.message shouldBe "Language must not be blank and two charters long!"
        }
    }

    @Test fun `similaritySearch() Returns 400 when Game is not valid`() {
        val requestJson = aChunkSearchBySimilarityRequestJson(game = aRandomUniqueString())
        val responseBody: String = mockMvc.perform(
            post("/chunks/similarity-search")
                .contentType("application/json")
                .content(requestJson))
            .andExpect(status().isBadRequest)
            .andReturn().response.contentAsString

        verify(exactly = 0) { embeddingsDao.searchBySimilarity(any(), any(), any(), any(), any(), any()) }
        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Game is not valid!"
    }

    @TestFactory
    fun `similaritySearch() returns 400 when Game is missing`() =
        listOf("").map { //TODO add null case
            dynamicTest(" -> '$it'") {
                val requestJson = aChunkSearchBySimilarityRequestJson(game = it)
                val responseBody: String = mockMvc.perform(
                    post("/chunks/similarity-search")
                        .contentType("application/json")
                        .content(requestJson))
                    .andExpect(status().isBadRequest)
                    .andReturn().response.contentAsString

                verify(exactly = 0) { embeddingsDao.searchBySimilarity(any(), any(), any(), any(), any(), any()) }
                val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
                response.message shouldBe "Game is missing!"
            }
        }

    @Test fun `similaritySearch() Returns 400 when Topic is not valid`() {
        val requestJson = aChunkSearchBySimilarityRequestJson(topic = aRandomUniqueString())
        val responseBody: String = mockMvc.perform(
            post("/chunks/similarity-search")
                .contentType("application/json")
                .content(requestJson))
            .andExpect(status().isBadRequest)
            .andReturn().response.contentAsString

        verify(exactly = 0) { embeddingsDao.searchBySimilarity(any(), any(), any(), any(), any(), any()) }
        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Topic is not valid!"
    }

    @TestFactory
    fun `similaritySearch() returns 400 when Topic is missing`() =
        listOf("").map { //TODO add null case
            dynamicTest(" -> '$it'") {
                val requestJson = aChunkSearchBySimilarityRequestJson(topic = it)
                val responseBody: String = mockMvc.perform(
                    post("/chunks/similarity-search")
                        .contentType("application/json")
                        .content(requestJson))
                    .andExpect(status().isBadRequest)
                    .andReturn().response.contentAsString

                verify(exactly = 0) { embeddingsDao.searchBySimilarity(any(), any(), any(), any(), any(), any()) }
                val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
                response.message shouldBe "Topic is missing!"
            }
        }

    @TestFactory
    fun `similaritySearch() returns 400 when embeddings limit is not valid`() =
        listOf(-3, 0).map {
            dynamicTest(" -> '$it'") {
                val requestJson = aChunkSearchBySimilarityRequestJson(embeddingsLimit = it)
                val responseBody: String = mockMvc.perform(
                    post("/chunks/similarity-search")
                        .contentType("application/json")
                        .content(requestJson))
                    .andExpect(status().isBadRequest)
                    .andReturn().response.contentAsString

                verify(exactly = 0) { embeddingsDao.searchBySimilarity(any(), any(), any(), any(), any(), any()) }
                val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
                response.message shouldBe "'embeddingsLimit' must be a positive Int!"
            }
        }

    @TestFactory
    fun `similaritySearch() returns 400 when distance limit is not valid`() =
        listOf(-3.1, 0.0, 2.0, 356.99).map {
            dynamicTest(" -> '$it'") {
                val requestJson = aChunkSearchBySimilarityRequestJson(distanceLimit = it)
                val responseBody: String = mockMvc.perform(
                    post("/chunks/similarity-search")
                        .contentType("application/json")
                        .content(requestJson))
                    .andExpect(status().isBadRequest)
                    .andReturn().response.contentAsString

                verify(exactly = 0) { embeddingsDao.searchBySimilarity(any(), any(), any(), any(), any(), any()) }
                val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
                response.message shouldBe "'distanceLimit' must be in (0.0 ; 2.0)!"
            }
        }

    @Test fun `similaritySearch() Returns 500 when fails to create request text embedding`() {
        val request = aChunkSearchBySimilarityRequest()
        coEvery {
            embeddingsProvider.createEmbedding(request.text, false)
        } returns EmbeddingCreationFailure("an embedding failure").left()

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
        coEvery { embeddingsProvider.createEmbedding(request.text, false) } returns targetEmbedding.right()
        every {
            embeddingsDao.searchBySimilarity(targetEmbedding, game, topic, request.language, any(), any())
        } throws failure

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
