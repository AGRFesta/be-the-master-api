package org.agrfesta.btm.api.controllers

import arrow.core.left
import arrow.core.right
import com.ninjasquad.springmockk.MockkBean
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.restassured.RestAssured
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.agrfesta.btm.api.model.EmbeddingCreationFailure
import org.agrfesta.btm.api.model.EmbeddingStatus.EMBEDDED
import org.agrfesta.btm.api.model.EmbeddingStatus.UNEMBEDDED
import org.agrfesta.btm.api.model.Game
import org.agrfesta.btm.api.model.Topic
import org.agrfesta.btm.api.persistence.TestingTextBitsRepository
import org.agrfesta.btm.api.persistence.jdbc.entities.TranslationEntity
import org.agrfesta.btm.api.persistence.jdbc.entities.aTranslationEntity
import org.agrfesta.btm.api.persistence.jdbc.repositories.EmbeddingRepository
import org.agrfesta.btm.api.persistence.jdbc.repositories.TextBitsRepository
import org.agrfesta.btm.api.persistence.jdbc.repositories.TranslationsRepository
import org.agrfesta.btm.api.services.EmbeddingsProvider
import org.agrfesta.btm.api.services.utils.RandomGenerator
import org.agrfesta.btm.api.services.utils.TimeService
import org.agrfesta.btm.api.services.utils.toNoNanoSec
import org.agrfesta.test.mothers.aRandomUniqueString
import org.agrfesta.test.mothers.anEmbedding
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
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
class TextBitsControllerIntegrationTest(
    @Autowired private val testTextBitsRepo: TestingTextBitsRepository,
    @Autowired private val textBitsRepo: TextBitsRepository,
    @Autowired private val embeddingRepo: EmbeddingRepository,
    @Autowired private val translationsRepo: TranslationsRepository,
    @Autowired @MockkBean private val embeddingsProvider: EmbeddingsProvider,
    @Autowired @MockkBean private val randomGenerator: RandomGenerator,
    @Autowired @MockkBean private val timeService: TimeService
) {

    companion object {
        @Container
        @ServiceConnection
        val postgres: PostgreSQLContainer<*> = DockerImageName.parse("pgvector/pgvector:pg16")
            .asCompatibleSubstituteFor("postgres")
            .let { PostgreSQLContainer(it) }
    }

    @LocalServerPort
    private val port: Int? = null

    private val uuid: UUID = UUID.randomUUID()
    private val now = Instant.now().toNoNanoSec()
    private val text = aRandomUniqueString()
    private val topic = Topic.RULE

    @BeforeEach
    fun setUp() {
        RestAssured.baseURI = "http://localhost:$port"

        every { randomGenerator.uuid() } returns uuid
        every { timeService.nowNoNano() } returns now
    }

    ///// createTextBit ////////////////////////////////////////////////////////////////////////////////////////////////

    @Test fun `createTextBit() Creates text bit and embeddings for original text and translations`() {
        val original = aTranslation(language = "en")
        val itTranslation = aTranslation(language = "it")
        val frTranslation = aTranslation(language = "fr")
        val request = aTextBitCreationRequest(
            originalText = original,
            translations = listOf(itTranslation, frTranslation)
        )
        val originalEmbedding = anEmbedding()
        val itEmbedding = anEmbedding()
        val frEmbedding = anEmbedding()
        val uuidList = listOf(
            uuid,
            UUID.randomUUID(), // original translation
            UUID.randomUUID(), // original embedding
            UUID.randomUUID(), // it translation
            UUID.randomUUID(), // it embedding
            UUID.randomUUID(), // fr translation
            UUID.randomUUID()  // fr embedding
        )
        every { randomGenerator.uuid() } returnsMany uuidList
        coEvery { embeddingsProvider.createEmbedding(original.text) } returns originalEmbedding.right()
        coEvery { embeddingsProvider.createEmbedding(itTranslation.text) } returns itEmbedding.right()
        coEvery { embeddingsProvider.createEmbedding(frTranslation.text) } returns frEmbedding.right()

        val result = given()
            .contentType(ContentType.JSON)
            .body(request.toJsonString())
            .`when`()
            .post("/text-bits")
            .then()
            .statusCode(200)
            .extract()
            .`as`(MessageResponse::class.java)

        result.message shouldBe "Text bit successfully persisted!"
        val textBit = testTextBitsRepo.findById(uuid)
        textBit.shouldNotBeNull()
        textBit.game shouldBe request.game.name
        textBit.topic shouldBe request.topic.name
        textBit.createdOn shouldBe now
        textBit.updatedOn.shouldBeNull()
        val translations = translationsRepo.findTranslations(uuid)
        translations.map { listOf(it.text, it.languageCode, it.original, it.embeddingStatus, it.createdOn) }
            .shouldContainExactlyInAnyOrder(
                listOf(itTranslation.text, itTranslation.language, false, EMBEDDED, now),
                listOf(original.text, original.language, true, EMBEDDED, now),
                listOf(frTranslation.text, frTranslation.language, false, EMBEDDED, now)
            )
        val embeddings = translations.mapNotNull { embeddingRepo.findEmbeddingByTranslationId(it.id) }
        embeddings.map { it.vector.toList() to it.createdOn }.shouldContainExactlyInAnyOrder(
            frEmbedding.toList() to now,
            originalEmbedding.toList() to now,
            itEmbedding.toList() to now
        )
    }

    @Test fun `createTextBit() Creates text bit and translations only, when inBatch is true`() {
        val original = aTranslation(language = "en")
        val itTranslation = aTranslation(language = "it")
        val frTranslation = aTranslation(language = "fr")
        val request = aTextBitCreationRequest(
            originalText = original,
            translations = listOf(itTranslation, frTranslation),
            inBatch = true
        )
        val uuidList = listOf(
            uuid,
            UUID.randomUUID(), // original translation
            UUID.randomUUID(), // it translation
            UUID.randomUUID(), // fr translation
        )
        every { randomGenerator.uuid() } returnsMany uuidList

        val result = given()
            .contentType(ContentType.JSON)
            .body(request.toJsonString())
            .`when`()
            .post("/text-bits")
            .then()
            .statusCode(200)
            .extract()
            .`as`(MessageResponse::class.java)

        result.message shouldBe "Text bit successfully persisted!"
        testTextBitsRepo.findById(uuid).shouldNotBeNull()
        val translations = translationsRepo.findTranslations(uuid)
        translations.map { listOf(it.text, it.languageCode, it.original, it.embeddingStatus, it.createdOn) }
            .shouldContainExactlyInAnyOrder(
                listOf(itTranslation.text, itTranslation.language, false, UNEMBEDDED, now),
                listOf(original.text, original.language, true, UNEMBEDDED, now),
                listOf(frTranslation.text, frTranslation.language, false, UNEMBEDDED, now)
            )
        val embeddings = translations.mapNotNull { embeddingRepo.findEmbeddingByTranslationId(it.id) }
        embeddings.shouldBeEmpty()
    }

    //TODO add simple test when inBatch is missing

    @Test fun `createTextBit() Creates text bit and translations only, when embedding creation fails`() {
        val original = aTranslation(language = "en")
        val itTranslation = aTranslation(language = "it")
        val frTranslation = aTranslation(language = "fr")
        val request = aTextBitCreationRequest(
            originalText = original,
            translations = listOf(itTranslation, frTranslation)
        )
        val itEmbedding = anEmbedding()
        val uuidList = listOf(
            uuid,
            UUID.randomUUID(), // original translation
            UUID.randomUUID(), // it translation
            UUID.randomUUID(), // it embedding
            UUID.randomUUID()  // fr translation
        )
        every { randomGenerator.uuid() } returnsMany uuidList
        coEvery { embeddingsProvider.createEmbedding(original.text) } returns EmbeddingCreationFailure.left()
        coEvery { embeddingsProvider.createEmbedding(itTranslation.text) } returns itEmbedding.right()
        coEvery { embeddingsProvider.createEmbedding(frTranslation.text) } returns EmbeddingCreationFailure.left()

        val result = given()
            .contentType(ContentType.JSON)
            .body(request.toJsonString())
            .`when`()
            .post("/text-bits")
            .then()
            .statusCode(200)
            .extract()
            .`as`(MessageResponse::class.java)

        result.message shouldBe
                "Text bit successfully persisted! Failed embeddings creation for languages [en, fr]"
        testTextBitsRepo.findById(uuid).shouldNotBeNull()
        val translations = translationsRepo.findTranslations(uuid)
        translations.map { listOf(it.text, it.languageCode, it.original, it.embeddingStatus, it.createdOn) }
            .shouldContainExactlyInAnyOrder(
                listOf(itTranslation.text, itTranslation.language, false, EMBEDDED, now),
                listOf(original.text, original.language, true, UNEMBEDDED, now),
                listOf(frTranslation.text, frTranslation.language, false, UNEMBEDDED, now)
            )
        val embeddings = translations.mapNotNull { embeddingRepo.findEmbeddingByTranslationId(it.id) }
        embeddings.map { it.vector.toList() to it.createdOn }.shouldContainExactlyInAnyOrder(
            itEmbedding.toList() to now
        )
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    ///// update ///////////////////////////////////////////////////////////////////////////////////////////////////////

    @Test fun `update() Returns 404 when text bit is missing`() {
        testTextBitsRepo.findById(uuid).shouldBeNull()

        val result = given()
            .contentType(ContentType.JSON)
            .body(aTextBitTranslationsPatchRequest().toJsonString())
            .`when`()
            .patch("/text-bits/$uuid")
            .then()
            .statusCode(404)
            .extract()
            .`as`(MessageResponse::class.java)

        result.message shouldBe "Text bit $uuid is missing!"
        testTextBitsRepo.findById(uuid).shouldBeNull()
    }

    @Test fun `update() Add translations when missing`() {
        val request = aTextBitTranslationsPatchRequest(inBatch = true, language = "de")
        val uuid: UUID = UUID.randomUUID()
        val creationTime = now.minusSeconds(50_000)
        textBitsRepo.insert(uuid, Game.MAUSRITTER, topic, creationTime)
        val original = aTranslationEntity(textBitId = uuid, original = true, languageCode = "en")
        val itTranslation = aTranslationEntity(textBitId = uuid, original = false, languageCode = "it")
        val frTranslation = aTranslationEntity(textBitId = uuid, original = false, languageCode = "fr")
        translationsRepo.insert(original)
        translationsRepo.insert(itTranslation)
        translationsRepo.insert(frTranslation)

        val result = given()
            .contentType(ContentType.JSON)
            .body(request)
            .`when`()
            .patch("/text-bits/$uuid")
            .then()
            .statusCode(200)
            .extract()
            .`as`(MessageResponse::class.java)

        result.message shouldBe "Text bit $uuid successfully patched!"
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
            textBitsRepo.insert(uuid, Game.MAUSRITTER, topic, now)
            every { randomGenerator.uuid() } returns UUID.randomUUID()
            val embedding = anEmbedding()
            coEvery { embeddingsProvider.createEmbedding(text) } returns embedding.right()

            val result = given()
                .contentType(ContentType.JSON)
                .body(it)
                .`when`()
                .patch("/text-bits/$uuid")
                .then()
                .statusCode(200)
                .extract()
                .`as`(MessageResponse::class.java)

            result.message shouldBe "Text bit $uuid successfully patched!"
            val translation = translationsRepo.findTranslationByLanguage(uuid, "it").shouldNotBeNull()
            translation.embeddingStatus shouldBe EMBEDDED
            translation.text shouldBe text
            val embeddingEntity = embeddingRepo.findEmbeddingByTranslationId(translation.id).shouldNotBeNull()
            embeddingEntity.vector shouldBe embedding
        }
    }

    @Test fun `update() Replace text bit text and embedding when inBatch is false`() {
        val originalText = aRandomUniqueString()
        val embedding = anEmbedding()
        val newEmbedding = anEmbedding()
        val request = aTextBitTranslationsPatchRequest(language = "it", inBatch = false)
        val creationTime = now.minusSeconds(50_000)
        textBitsRepo.insert(uuid, Game.MAUSRITTER, topic, creationTime)
        val translationId = UUID.randomUUID()
        translationsRepo.insert(
            TranslationEntity(
                translationId,
                uuid,
                request.language,
                true,
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
            .patch("/text-bits/$uuid")
            .then()
            .statusCode(200)
            .extract()
            .`as`(MessageResponse::class.java)

        result.message shouldBe "Text bit $uuid successfully patched!"
        val translation = translationsRepo.findTranslationByLanguage(uuid, request.language).shouldNotBeNull()
        translation.embeddingStatus shouldBe EMBEDDED
        translation.text shouldBe request.text
        val embeddingEntity = embeddingRepo.findEmbeddingByTranslationId(translation.id).shouldNotBeNull()
        embeddingEntity.vector shouldBe newEmbedding
    }

    @Test fun `update() Replace a translation removing old embedding when new embedding creation fails`() {
        val originalText = aRandomUniqueString()
        val embedding = anEmbedding()
        val request = aTextBitTranslationsPatchRequest(language = "it", inBatch = false)
        val creationTime = now.minusSeconds(50_000)
        textBitsRepo.insert(uuid, Game.MAUSRITTER, topic, creationTime)
        val translationId = UUID.randomUUID()
        translationsRepo.insert(
            TranslationEntity(
                translationId,
                uuid,
                request.language,
                true,
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
            .patch("/text-bits/$uuid")
            .then()
            .statusCode(200)
            .extract()
            .`as`(MessageResponse::class.java)

        result.message shouldBe "Text bit $uuid successfully patched! But embedding creation failed!"
        val translation = translationsRepo.findTranslationByLanguage(uuid, request.language).shouldNotBeNull()
        translation.embeddingStatus shouldBe UNEMBEDDED
        translation.text shouldBe request.text
        embeddingRepo.findEmbeddingByTranslationId(translation.id).shouldBeNull()
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

}
