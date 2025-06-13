package org.agrfesta.btm.api.controllers

import arrow.core.left
import arrow.core.right
import com.ninjasquad.springmockk.MockkBean
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.restassured.RestAssured.given
import io.restassured.common.mapper.TypeRef
import io.restassured.http.ContentType
import org.agrfesta.btm.api.model.Embedding
import org.agrfesta.btm.api.model.EmbeddingCreationFailure
import org.agrfesta.btm.api.model.EmbeddingStatus.EMBEDDED
import org.agrfesta.btm.api.model.EmbeddingStatus.UNEMBEDDED
import org.agrfesta.btm.api.model.Game
import org.agrfesta.btm.api.model.Topic
import org.agrfesta.btm.api.persistence.TestingChunksRepository
import org.agrfesta.btm.api.persistence.jdbc.entities.TranslationEntity
import org.agrfesta.btm.api.persistence.jdbc.entities.aTranslationEntity
import org.agrfesta.btm.api.persistence.jdbc.repositories.ChunksRepository
import org.agrfesta.btm.api.persistence.jdbc.repositories.EmbeddingRepository
import org.agrfesta.btm.api.persistence.jdbc.repositories.TranslationsRepository
import org.agrfesta.btm.api.services.EmbeddingsProvider
import org.agrfesta.btm.api.services.utils.TimeService
import org.agrfesta.btm.api.services.utils.toNoNanoSec
import org.agrfesta.test.mothers.aNormalizedEmbedding
import org.agrfesta.test.mothers.aRandomUniqueString
import org.agrfesta.test.mothers.anEmbedding
import org.agrfesta.test.mothers.generateVectorWithDistance
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.junit.jupiter.Container
import java.time.Instant
import java.util.*

class ChunksControllerIntegrationTest(
    @Autowired private val testChunksRepo: TestingChunksRepository,
    @Autowired private val chunksRepo: ChunksRepository,
    @Autowired private val embeddingRepo: EmbeddingRepository,
    @Autowired private val translationsRepo: TranslationsRepository,
    @Autowired @MockkBean private val embeddingsProvider: EmbeddingsProvider,
    @Autowired @MockkBean private val timeService: TimeService
): AbstractIntegrationTest() {

    companion object {
        @Container
        @ServiceConnection
        val postgres = createPostgresContainer()
    }

    private val uuid: UUID = UUID.randomUUID()
    private val now = Instant.now().toNoNanoSec()
    private val text = aRandomUniqueString()
    private val game = aGame()
    private val topic = aTopic()
    private val language = aLanguage()

    @BeforeEach
    fun defaultMockBehaviourSetup() {
        every { timeService.nowNoNano() } returns now
    }

    ///// createChunks /////////////////////////////////////////////////////////////////////////////////////////////////

    @Test fun `createChunks() Creates and embed all chunks when embed is true`() {
        val textA = aRandomUniqueString()
        val textB = aRandomUniqueString()
        val textC = aRandomUniqueString()
        val request = aChunksCreationRequestJson(
            game = game.name,
            topic = topic.name,
            language = language,
            embed = true,
            texts = listOf(textA, textB, textC))
        val embA = anEmbedding()
        val embB = anEmbedding()
        val embC = anEmbedding()
        coEvery { embeddingsProvider.createEmbedding(textA) } returns embA.right()
        coEvery { embeddingsProvider.createEmbedding(textB) } returns embB.right()
        coEvery { embeddingsProvider.createEmbedding(textC) } returns embC.right()

        val result = given()
            .contentType(ContentType.JSON)
            .body(request)
            .`when`()
            .post("/chunks")
            .then()
            .statusCode(200)
            .extract()
            .`as`(MessageResponse::class.java)

        result.message shouldBe "3 Chunks successfully persisted!"

        val tweA = testChunksRepo.getTranslationWithEmbedding(language, textA).shouldNotBeNull()
        tweA.vector shouldBe embA
        tweA.embeddingStatus shouldBe EMBEDDED.name
        val chunkA = testChunksRepo.findById(tweA.chunkId).shouldNotBeNull()
        chunkA.game shouldBe game.name
        chunkA.topic shouldBe topic.name
        chunkA.createdOn shouldBe now
        chunkA.updatedOn.shouldBeNull()

        val tweB = testChunksRepo.getTranslationWithEmbedding(language, textB).shouldNotBeNull()
        tweB.vector shouldBe embB
        tweB.embeddingStatus shouldBe EMBEDDED.name
        val chunkB = testChunksRepo.findById(tweB.chunkId).shouldNotBeNull()
        chunkB.game shouldBe game.name
        chunkB.topic shouldBe topic.name
        chunkB.createdOn shouldBe now
        chunkB.updatedOn.shouldBeNull()

        val tweC = testChunksRepo.getTranslationWithEmbedding(language, textC).shouldNotBeNull()
        tweC.vector shouldBe embC
        tweC.embeddingStatus shouldBe EMBEDDED.name
        val chunkC = testChunksRepo.findById(tweC.chunkId).shouldNotBeNull()
        chunkC.game shouldBe game.name
        chunkC.topic shouldBe topic.name
        chunkC.createdOn shouldBe now
        chunkC.updatedOn.shouldBeNull()
    }

    @Test fun `createChunks() Ignores failures creating embeddings but mark chunk as not embedded`() {
        val textA = aRandomUniqueString()
        val textB = aRandomUniqueString()
        val textC = aRandomUniqueString()
        val request = aChunksCreationRequestJson(
            game = game.name,
            topic = topic.name,
            language = language,
            embed = true,
            texts = listOf(textA, textB, textC))
        val embA = anEmbedding()
        val embC = anEmbedding()
        coEvery { embeddingsProvider.createEmbedding(textA) } returns embA.right()
        coEvery { embeddingsProvider.createEmbedding(textB) } returns EmbeddingCreationFailure.left()
        coEvery { embeddingsProvider.createEmbedding(textC) } returns embC.right()

        val result = given()
            .contentType(ContentType.JSON)
            .body(request)
            .`when`()
            .post("/chunks")
            .then()
            .statusCode(200)
            .extract()
            .`as`(MessageResponse::class.java)

        result.message shouldBe "3 Chunks successfully persisted!"

        val tweA = testChunksRepo.getTranslationWithEmbedding(language, textA).shouldNotBeNull()
        tweA.vector shouldBe embA
        tweA.embeddingStatus shouldBe EMBEDDED.name
        testChunksRepo.findById(tweA.chunkId).shouldNotBeNull()

        val tweB = testChunksRepo.getTranslationWithEmbedding(language, textB).shouldNotBeNull()
        tweB.vector.shouldBeNull()
        tweB.embeddingStatus shouldBe UNEMBEDDED.name
        testChunksRepo.findById(tweB.chunkId).shouldNotBeNull()

        val tweC = testChunksRepo.getTranslationWithEmbedding(language, textC).shouldNotBeNull()
        tweC.vector shouldBe embC
        tweC.embeddingStatus shouldBe EMBEDDED.name
        testChunksRepo.findById(tweC.chunkId).shouldNotBeNull()
    }

    @Test fun `createChunks() Creates and embed all chunks when embed is missing`() {
        val textA = aRandomUniqueString()
        val textB = aRandomUniqueString()
        val textC = aRandomUniqueString()
        val textD = aRandomUniqueString()
        val request = aChunksCreationRequestJson(
            game = game.name,
            topic = topic.name,
            language = language,
            embed = null,
            texts = listOf(textA, textB, textC, textD))
        val embA = anEmbedding()
        val embB = anEmbedding()
        val embC = anEmbedding()
        val embD = anEmbedding()
        coEvery { embeddingsProvider.createEmbedding(textA) } returns embA.right()
        coEvery { embeddingsProvider.createEmbedding(textB) } returns embB.right()
        coEvery { embeddingsProvider.createEmbedding(textC) } returns embC.right()
        coEvery { embeddingsProvider.createEmbedding(textD) } returns embD.right()

        val result = given()
            .contentType(ContentType.JSON)
            .body(request)
            .`when`()
            .post("/chunks")
            .then()
            .statusCode(200)
            .extract()
            .`as`(MessageResponse::class.java)

        result.message shouldBe "4 Chunks successfully persisted!"

        val tweA = testChunksRepo.getTranslationWithEmbedding(language, textA).shouldNotBeNull()
        tweA.vector shouldBe embA
        tweA.embeddingStatus shouldBe EMBEDDED.name

        val tweB = testChunksRepo.getTranslationWithEmbedding(language, textB).shouldNotBeNull()
        tweB.vector shouldBe embB
        tweB.embeddingStatus shouldBe EMBEDDED.name

        val tweC = testChunksRepo.getTranslationWithEmbedding(language, textC).shouldNotBeNull()
        tweC.vector shouldBe embC
        tweC.embeddingStatus shouldBe EMBEDDED.name

        val tweD = testChunksRepo.getTranslationWithEmbedding(language, textD).shouldNotBeNull()
        tweD.vector shouldBe embD
        tweD.embeddingStatus shouldBe EMBEDDED.name

    }

    @Test fun `createChunks() Do not embed any chunk when embed is false`() {
        val textA = aRandomUniqueString()
        val textB = aRandomUniqueString()
        val request = aChunksCreationRequestJson(
            game = game.name,
            topic = topic.name,
            language = language,
            embed = false,
            texts = listOf(textA, textB))

        val result = given()
            .contentType(ContentType.JSON)
            .body(request)
            .`when`()
            .post("/chunks")
            .then()
            .statusCode(200)
            .extract()
            .`as`(MessageResponse::class.java)

        result.message shouldBe "2 Chunks successfully persisted!"
        coVerify(exactly = 0) { embeddingsProvider.createEmbedding(any()) }

        val tweA = testChunksRepo.getTranslationWithEmbedding(language, textA).shouldNotBeNull()
        tweA.vector.shouldBeNull()
        tweA.embeddingStatus shouldBe UNEMBEDDED.name
        val chunkA = testChunksRepo.findById(tweA.chunkId).shouldNotBeNull()
        chunkA.game shouldBe game.name
        chunkA.topic shouldBe topic.name
        chunkA.createdOn shouldBe now
        chunkA.updatedOn.shouldBeNull()

        val tweB = testChunksRepo.getTranslationWithEmbedding(language, textB).shouldNotBeNull()
        tweB.vector.shouldBeNull()
        tweB.embeddingStatus shouldBe UNEMBEDDED.name
        val chunkB = testChunksRepo.findById(tweB.chunkId).shouldNotBeNull()
        chunkB.game shouldBe game.name
        chunkB.topic shouldBe topic.name
        chunkB.createdOn shouldBe now
        chunkB.updatedOn.shouldBeNull()
    }

    @Test fun `createChunks() Ignores blank elements in texts`() {
        val request = aChunksCreationRequestJson(
            game = game.name,
            topic = topic.name,
            language = language,
            embed = false,
            texts = listOf("", text, " ", "  "))

        val result = given()
            .contentType(ContentType.JSON)
            .body(request)
            .`when`()
            .post("/chunks")
            .then()
            .statusCode(200)
            .extract()
            .`as`(MessageResponse::class.java)

        result.message shouldBe "1 Chunks successfully persisted!"
        testChunksRepo.getTranslationWithEmbedding(language, text).shouldNotBeNull()
    }

    @Test fun `createChunks() Ignores duplicate elements in texts`() {
        val textA = aRandomUniqueString()
        val textB = aRandomUniqueString()
        val request = aChunksCreationRequestJson(
            game = game.name,
            topic = topic.name,
            language = language,
            embed = false,
            texts = listOf("", textA, textB, textB, "  ", textB, textA))

        val result = given()
            .contentType(ContentType.JSON)
            .body(request)
            .`when`()
            .post("/chunks")
            .then()
            .statusCode(200)
            .extract()
            .`as`(MessageResponse::class.java)

        result.message shouldBe "2 Chunks successfully persisted!"
        testChunksRepo.getTranslationWithEmbedding(language, textB).shouldNotBeNull()
        testChunksRepo.getTranslationWithEmbedding(language, textA).shouldNotBeNull()
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    ///// update ///////////////////////////////////////////////////////////////////////////////////////////////////////

    @Test fun `update() Returns 404 when chunk is missing`() {
        testChunksRepo.findById(uuid).shouldBeNull()

        val result = given()
            .contentType(ContentType.JSON)
            .body(aChunkTranslationsPatchRequest().toJsonString())
            .`when`()
            .patch("/chunks/$uuid")
            .then()
            .statusCode(404)
            .extract()
            .`as`(MessageResponse::class.java)

        result.message shouldBe "Chunk $uuid is missing!"
        testChunksRepo.findById(uuid).shouldBeNull()
    }

    @Test fun `update() Add translations when missing`() {
        val request = aChunkTranslationsPatchRequest(inBatch = true, language = "de")
        val uuid: UUID = UUID.randomUUID()
        val creationTime = now.minusSeconds(50_000)
        chunksRepo.insert(uuid, Game.MAUSRITTER, topic, creationTime)
        val original = aTranslationEntity(chunkId = uuid, languageCode = "en")
        val itTranslation = aTranslationEntity(chunkId = uuid, languageCode = "it")
        val frTranslation = aTranslationEntity(chunkId = uuid, languageCode = "fr")
        translationsRepo.insert(original)
        translationsRepo.insert(itTranslation)
        translationsRepo.insert(frTranslation)

        val result = given()
            .contentType(ContentType.JSON)
            .body(request)
            .`when`()
            .patch("/chunks/$uuid")
            .then()
            .statusCode(200)
            .extract()
            .`as`(MessageResponse::class.java)

        result.message shouldBe "Chunk $uuid successfully patched!"
        val translation = translationsRepo.findTranslationByLanguage(uuid, request.language).shouldNotBeNull()
        translation.embeddingStatus shouldBe UNEMBEDDED
        translation.text shouldBe request.text
        embeddingRepo.findEmbeddingByTranslationId(translation.id).shouldBeNull()
    }

    @TestFactory
    fun `update() Embed new text translation, when inBatch is false or not specified`() =
        listOf(
        """{"text": "$text", "language": "it", "inBatch": false}""",
        """{"text": "$text", "language": "it", "inBatch": null}""",
        """{"text": "$text", "language": "it"}"""
    ).map {
        dynamicTest(" -> '$it'") {
            val uuid: UUID = UUID.randomUUID()
            chunksRepo.insert(uuid, Game.MAUSRITTER, topic, now)
            val embedding = anEmbedding()
            coEvery { embeddingsProvider.createEmbedding(text) } returns embedding.right()

            val result = given()
                .contentType(ContentType.JSON)
                .body(it)
                .`when`()
                .patch("/chunks/$uuid")
                .then()
                .statusCode(200)
                .extract()
                .`as`(MessageResponse::class.java)

            result.message shouldBe "Chunk $uuid successfully patched!"
            val translation = translationsRepo.findTranslationByLanguage(uuid, "it").shouldNotBeNull()
            translation.embeddingStatus shouldBe EMBEDDED
            translation.text shouldBe text
            val embeddingEntity = embeddingRepo.findEmbeddingByTranslationId(translation.id).shouldNotBeNull()
            embeddingEntity.vector shouldBe embedding
        }
    }

    @Test fun `update() Replace chunk text and embedding when inBatch is false`() {
        val originalText = aRandomUniqueString()
        val embedding = anEmbedding()
        val newEmbedding = anEmbedding()
        val request = aChunkTranslationsPatchRequest(language = "it", inBatch = false)
        val creationTime = now.minusSeconds(50_000)
        chunksRepo.insert(uuid, Game.MAUSRITTER, topic, creationTime)
        val translationId = UUID.randomUUID()
        translationsRepo.insert(
            TranslationEntity(
                translationId,
                uuid,
                request.language,
                originalText,
                EMBEDDED,
                creationTime))
        val embeddingId: UUID = UUID.randomUUID()
        embeddingRepo.insertEmbedding(embeddingId, translationId, embedding, now)
        coEvery { embeddingsProvider.createEmbedding(request.text) } returns newEmbedding.right()

        val result = given()
            .contentType(ContentType.JSON)
            .body(request)
            .`when`()
            .patch("/chunks/$uuid")
            .then()
            .statusCode(200)
            .extract()
            .`as`(MessageResponse::class.java)

        result.message shouldBe "Chunk $uuid successfully patched!"
        val translation = translationsRepo.findTranslationByLanguage(uuid, request.language).shouldNotBeNull()
        translation.embeddingStatus shouldBe EMBEDDED
        translation.text shouldBe request.text
        val embeddingEntity = embeddingRepo.findEmbeddingByTranslationId(translation.id).shouldNotBeNull()
        embeddingEntity.vector shouldBe newEmbedding
    }

    @Test fun `update() Replace a translation removing old embedding when new embedding creation fails`() {
        val originalText = aRandomUniqueString()
        val embedding = anEmbedding().normalize()
        val request = aChunkTranslationsPatchRequest(language = "it", inBatch = false)
        val creationTime = now.minusSeconds(50_000)
        chunksRepo.insert(uuid, Game.MAUSRITTER, topic, creationTime)
        val translationId = UUID.randomUUID()
        translationsRepo.insert(
            TranslationEntity(
                translationId,
                uuid,
                request.language,
                originalText,
                EMBEDDED,
                creationTime))
        val embeddingId: UUID = UUID.randomUUID()
        embeddingRepo.insertEmbedding(embeddingId, translationId, embedding, now)
        coEvery { embeddingsProvider.createEmbedding(request.text) } returns EmbeddingCreationFailure.left()

        val result = given()
            .contentType(ContentType.JSON)
            .body(request)
            .`when`()
            .patch("/chunks/$uuid")
            .then()
            .statusCode(200)
            .extract()
            .`as`(MessageResponse::class.java)

        result.message shouldBe "Chunk $uuid successfully patched! But embedding creation failed!"
        val translation = translationsRepo.findTranslationByLanguage(uuid, request.language).shouldNotBeNull()
        translation.embeddingStatus shouldBe UNEMBEDDED
        translation.text shouldBe request.text
        embeddingRepo.findEmbeddingByTranslationId(translation.id).shouldBeNull()
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    ///// similaritySearch /////////////////////////////////////////////////////////////////////////////////////////////

    @Test fun `similaritySearch() Returns empty list when there are no chunks`() {
        val request = aChunkSearchBySimilarityRequest()
        val targetEmbedding = anEmbedding()
        coEvery { embeddingsProvider.createEmbedding(request.text) } returns targetEmbedding.right()

        val result = given()
            .contentType(ContentType.JSON)
            .body(request.toJsonString())
            .`when`()
            .post("/chunks/similarity-search")
            .then()
            .statusCode(200)
            .extract()
            .`as`(object : TypeRef<List<SimilarityResultItem>>() {})

        result.shouldBeEmpty()
    }

    @Test fun `similaritySearch() Returns similar chunks only, sorted by descending similarity`() {
        val game = aGame()
        val topic = aTopic()
        val language = aLanguage()
        val request = aChunkSearchBySimilarityRequest(game = game, language = language, topic = topic)
        val targetEmbedding = aNormalizedEmbedding()
        val embeddingA = generateVectorWithDistance(targetEmbedding, 0.5)
        val embeddingB = generateVectorWithDistance(targetEmbedding, 2.0)
        val embeddingC = generateVectorWithDistance(targetEmbedding, 0.01)
        val embeddingD = generateVectorWithDistance(targetEmbedding, 1.0)
        val embeddingE = generateVectorWithDistance(targetEmbedding, 0.59)
        givenChunkEmbedding(game = game, language = language, topic = topic, text = "text A", embedding = embeddingA)
        givenChunkEmbedding(game = game, language = language, topic = topic, text = "text B", embedding = embeddingB)
        givenChunkEmbedding(game = game, language = language, topic = topic, text = "text C", embedding = embeddingC)
        givenChunkEmbedding(game = game, language = language, topic = topic, text = "text D", embedding = embeddingD)
        givenChunkEmbedding(game = game, language = language, topic = topic, text = "text E", embedding = embeddingE)
        coEvery { embeddingsProvider.createEmbedding(request.text) } returns targetEmbedding.right()

        val result = given()
            .contentType(ContentType.JSON)
            .body(request.toJsonString())
            .`when`()
            .post("/chunks/similarity-search")
            .then()
            .statusCode(200)
            .extract()
            .`as`(object : TypeRef<List<SimilarityResultItem>>() {})

        result.map { it.text }.shouldContainExactly("text C", "text A", "text E")
    }

    @Test fun `similaritySearch() Do not returns same topic and language but different game texts`() {
        val game = aGame()
        val topic = aTopic()
        val language = aLanguage()
        val request = aChunkSearchBySimilarityRequest(game = game, language = language, topic = topic)
        val targetEmbedding = aNormalizedEmbedding()
        val embA = generateVectorWithDistance(targetEmbedding, 0.5)
        val embB = generateVectorWithDistance(targetEmbedding, 0.35)
        val embC = generateVectorWithDistance(targetEmbedding, 0.01)
        val embD = generateVectorWithDistance(targetEmbedding, 0.3)
        val embE = generateVectorWithDistance(targetEmbedding, 0.59)
        val anotherGame = (Game.entries - game).random()
        givenChunkEmbedding(game = game, language = language, topic = topic, text = "text A", embedding = embA)
        givenChunkEmbedding(game = anotherGame, language = language, topic = topic, text = "text B", embedding = embB)
        givenChunkEmbedding(game = anotherGame, language = language, topic = topic, text = "text C", embedding = embC)
        givenChunkEmbedding(game = game, language = language, topic = topic, text = "text D", embedding = embD)
        givenChunkEmbedding(game = game, language = language, topic = topic, text = "text E", embedding = embE)
        coEvery { embeddingsProvider.createEmbedding(request.text) } returns targetEmbedding.right()

        val result = given()
            .contentType(ContentType.JSON)
            .body(request.toJsonString())
            .`when`()
            .post("/chunks/similarity-search")
            .then()
            .statusCode(200)
            .extract()
            .`as`(object : TypeRef<List<SimilarityResultItem>>() {})

        result.map { it.text }.shouldContainExactly("text D", "text A", "text E")
    }

    @Test fun `similaritySearch() Do not returns same game and language but different topic texts`() {
        val game = aGame()
        val topic = aTopic()
        val language = aLanguage()
        val request = aChunkSearchBySimilarityRequest(game = game, language = language, topic = topic)
        val targetEmbedding = aNormalizedEmbedding()
        val embA = generateVectorWithDistance(targetEmbedding, 0.5)
        val embB = generateVectorWithDistance(targetEmbedding, 0.35)
        val embC = generateVectorWithDistance(targetEmbedding, 0.01)
        val embD = generateVectorWithDistance(targetEmbedding, 0.3)
        val embE = generateVectorWithDistance(targetEmbedding, 0.59)
        val anotherTopic = (Topic.entries - topic).random()
        givenChunkEmbedding(topic = topic, language = language, game = game, text = "text A", embedding = embA)
        givenChunkEmbedding(topic = anotherTopic, language = language, game = game, text = "text B", embedding = embB)
        givenChunkEmbedding(topic = anotherTopic, language = language, game = game, text = "text C", embedding = embC)
        givenChunkEmbedding(topic = topic, language = language, game = game, text = "text D", embedding = embD)
        givenChunkEmbedding(topic = topic, language = language, game = game, text = "text E", embedding = embE)
        coEvery { embeddingsProvider.createEmbedding(request.text) } returns targetEmbedding.right()

        val result = given()
            .contentType(ContentType.JSON)
            .body(request.toJsonString())
            .`when`()
            .post("/chunks/similarity-search")
            .then()
            .statusCode(200)
            .extract()
            .`as`(object : TypeRef<List<SimilarityResultItem>>() {})

        result.map { it.text }.shouldContainExactly("text D", "text A", "text E")
    }

    @Test fun `similaritySearch() Do not returns same game and topic but different language texts`() {
        val game = aGame()
        val topic = aTopic()
        val language = aLanguage()
        val request = aChunkSearchBySimilarityRequest(game = game, topic = topic, language = language)
        val targetEmbedding = aNormalizedEmbedding()
        val embeddingA = generateVectorWithDistance(targetEmbedding, 0.5)
        val embeddingB = generateVectorWithDistance(targetEmbedding, 0.35)
        val embeddingC = generateVectorWithDistance(targetEmbedding, 0.01)
        val embeddingD = generateVectorWithDistance(targetEmbedding, 0.3)
        val embeddingE = generateVectorWithDistance(targetEmbedding, 0.59)
        val another = language.reversed()
        givenChunkEmbedding(language = language, topic = topic, game = game, text = "text A", embedding = embeddingA)
        givenChunkEmbedding(language = another, topic = topic, game = game, text = "text B", embedding = embeddingB)
        givenChunkEmbedding(language = another, topic = topic, game = game, text = "text C", embedding = embeddingC)
        givenChunkEmbedding(language = language, topic = topic, game = game, text = "text D", embedding = embeddingD)
        givenChunkEmbedding(language = language, topic = topic, game = game, text = "text E", embedding = embeddingE)
        coEvery { embeddingsProvider.createEmbedding(request.text) } returns targetEmbedding.right()

        val result = given()
            .contentType(ContentType.JSON)
            .body(request.toJsonString())
            .`when`()
            .post("/chunks/similarity-search")
            .then()
            .statusCode(200)
            .extract()
            .`as`(object : TypeRef<List<SimilarityResultItem>>() {})

        result.map { it.text }.shouldContainExactly("text D", "text A", "text E")
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private fun givenChunkEmbedding(
        game: Game = aGame(),
        topic: Topic = aTopic(),
        language: String = aLanguage(),
        text: String,
        embedding: Embedding
    ): UUID {
        val chunk = aChunk(game = game, topic = topic)
        chunksRepo.insert(chunk.id, game, topic, createdOn = now)
        val translation = aTranslationEntity(
            chunkId = chunk.id,
            embeddingStatus = EMBEDDED,
            text = text,
            languageCode = language)
        translationsRepo.insert(translation)
        embeddingRepo.insertEmbedding(UUID.randomUUID(), translation.id, embedding, now)
        return translation.id
    }

}
