package org.agrfesta.btm.api.controllers

import arrow.core.left
import arrow.core.right
import com.ninjasquad.springmockk.MockkBean
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.restassured.RestAssured
import io.restassured.RestAssured.given
import io.restassured.common.mapper.TypeRef
import io.restassured.http.ContentType
import org.agrfesta.btm.api.model.Embedding
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
import org.agrfesta.test.mothers.aNormalizedEmbedding
import org.agrfesta.test.mothers.aRandomUniqueString
import org.agrfesta.test.mothers.anEmbedding
import org.agrfesta.test.mothers.generateVectorWithDistance
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

    @Test fun `createTextBit() Creates text bit and embeddings for translation`() {
        val translation = aTranslation(language = "en")
        val request = aTextBitCreationRequest(translation = translation)
        val translationEmbedding = anEmbedding()
        val uuidList = listOf(
            uuid,
            UUID.randomUUID(), // translation
            UUID.randomUUID()  // embedding
        )
        every { randomGenerator.uuid() } returnsMany uuidList
        coEvery { embeddingsProvider.createEmbedding(translation.text) } returns translationEmbedding.right()

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

        val translations = translationsRepo.findTranslations(uuid).shouldHaveSize(1)
        val persisted = translations.first()
        persisted.text shouldBe translation.text
        persisted.languageCode shouldBe translation.language
        persisted.embeddingStatus shouldBe EMBEDDED
        persisted.createdOn shouldBe now
        val persistedEmbedding = embeddingRepo.findEmbeddingByTranslationId(persisted.id).shouldNotBeNull()
        persistedEmbedding.vector.toList() shouldBe translationEmbedding.toList()
    }

    @Test fun `createTextBit() Creates text bit and translations only, when inBatch is true`() {
        val translation = aTranslation(language = "en")
        val request = aTextBitCreationRequest(
            translation = translation,
            inBatch = true
        )
        val uuidList = listOf(
            uuid,
            UUID.randomUUID()  // translation
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

        val translations = translationsRepo.findTranslations(uuid).shouldHaveSize(1)
        val persisted = translations.first()
        persisted.text shouldBe translation.text
        persisted.languageCode shouldBe translation.language
        persisted.embeddingStatus shouldBe UNEMBEDDED
        persisted.createdOn shouldBe now
        embeddingRepo.findEmbeddingByTranslationId(persisted.id).shouldBeNull()
    }

    @Test fun `createTextBit() Creates text bit and translations only, when embedding creation fails`() {
        val translation = aTranslation(language = "en")
        val request = aTextBitCreationRequest(translation = translation, inBatch = false)
        val uuidList = listOf(
            uuid,
            UUID.randomUUID() // translation
        )
        every { randomGenerator.uuid() } returnsMany uuidList
        coEvery { embeddingsProvider.createEmbedding(translation.text) } returns EmbeddingCreationFailure.left()

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
                "Text bit successfully persisted! Failed embeddings creation."
        testTextBitsRepo.findById(uuid).shouldNotBeNull()
        val translations = translationsRepo.findTranslations(uuid).shouldHaveSize(1)
        val persisted = translations.first()
        persisted.text shouldBe translation.text
        persisted.languageCode shouldBe translation.language
        persisted.embeddingStatus shouldBe UNEMBEDDED
        persisted.createdOn shouldBe now
        embeddingRepo.findEmbeddingByTranslationId(persisted.id).shouldBeNull()
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
        val original = aTranslationEntity(textBitId = uuid, languageCode = "en")
        val itTranslation = aTranslationEntity(textBitId = uuid, languageCode = "it")
        val frTranslation = aTranslationEntity(textBitId = uuid, languageCode = "fr")
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
        val embedding = anEmbedding().normalize()
        val request = aTextBitTranslationsPatchRequest(language = "it", inBatch = false)
        val creationTime = now.minusSeconds(50_000)
        textBitsRepo.insert(uuid, Game.MAUSRITTER, topic, creationTime)
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

    ///// similaritySearch /////////////////////////////////////////////////////////////////////////////////////////////

    @Test fun `similaritySearch() Returns empty list when there are no text bits`() {
        val request = aTextBitSearchBySimilarityRequest()
        val targetEmbedding = anEmbedding()
        coEvery { embeddingsProvider.createEmbedding(request.text) } returns targetEmbedding.right()

        val result = given()
            .contentType(ContentType.JSON)
            .body(request.toJsonString())
            .`when`()
            .post("/text-bits/similarity-search")
            .then()
            .statusCode(200)
            .extract()
            .`as`(object : TypeRef<List<Pair<String, Double>>>() {})

        result.shouldBeEmpty()
    }

    @Test fun `similaritySearch() Returns similar text bits only, sorted by descending similarity`() {
        val game = aGame()
        val topic = aTopic()
        val language = aLanguage()
        val request = aTextBitSearchBySimilarityRequest(game = game, language = language, topic = topic)
        val targetEmbedding = aNormalizedEmbedding()
        val embeddingA = generateVectorWithDistance(targetEmbedding, 0.5)
        val embeddingB = generateVectorWithDistance(targetEmbedding, 2.0)
        val embeddingC = generateVectorWithDistance(targetEmbedding, 0.01)
        val embeddingD = generateVectorWithDistance(targetEmbedding, 1.0)
        val embeddingE = generateVectorWithDistance(targetEmbedding, 0.59)
        givenTextBitEmbedding(game = game, language = language, topic = topic, text = "text A", embedding = embeddingA)
        givenTextBitEmbedding(game = game, language = language, topic = topic, text = "text B", embedding = embeddingB)
        givenTextBitEmbedding(game = game, language = language, topic = topic, text = "text C", embedding = embeddingC)
        givenTextBitEmbedding(game = game, language = language, topic = topic, text = "text D", embedding = embeddingD)
        givenTextBitEmbedding(game = game, language = language, topic = topic, text = "text E", embedding = embeddingE)
        coEvery { embeddingsProvider.createEmbedding(request.text) } returns targetEmbedding.right()

        val result = given()
            .contentType(ContentType.JSON)
            .body(request.toJsonString())
            .`when`()
            .post("/text-bits/similarity-search")
            .then()
            .statusCode(200)
            .extract()
            .`as`(object : TypeRef<List<Pair<String, Double>>>() {})

        result.map { it.first }.shouldContainExactly("text C", "text A", "text E")
    }

    @Test fun `similaritySearch() Do not returns same topic and language but different game texts`() {
        val game = aGame()
        val topic = aTopic()
        val language = aLanguage()
        val request = aTextBitSearchBySimilarityRequest(game = game, language = language, topic = topic)
        val targetEmbedding = aNormalizedEmbedding()
        val embA = generateVectorWithDistance(targetEmbedding, 0.5)
        val embB = generateVectorWithDistance(targetEmbedding, 0.35)
        val embC = generateVectorWithDistance(targetEmbedding, 0.01)
        val embD = generateVectorWithDistance(targetEmbedding, 0.3)
        val embE = generateVectorWithDistance(targetEmbedding, 0.59)
        val anotherGame = (Game.entries - game).random()
        givenTextBitEmbedding(game = game, language = language, topic = topic, text = "text A", embedding = embA)
        givenTextBitEmbedding(game = anotherGame, language = language, topic = topic, text = "text B", embedding = embB)
        givenTextBitEmbedding(game = anotherGame, language = language, topic = topic, text = "text C", embedding = embC)
        givenTextBitEmbedding(game = game, language = language, topic = topic, text = "text D", embedding = embD)
        givenTextBitEmbedding(game = game, language = language, topic = topic, text = "text E", embedding = embE)
        coEvery { embeddingsProvider.createEmbedding(request.text) } returns targetEmbedding.right()

        val result = given()
            .contentType(ContentType.JSON)
            .body(request.toJsonString())
            .`when`()
            .post("/text-bits/similarity-search")
            .then()
            .statusCode(200)
            .extract()
            .`as`(object : TypeRef<List<Pair<String, Double>>>() {})

        result.map { it.first }.shouldContainExactly("text D", "text A", "text E")
    }

    @Test fun `similaritySearch() Do not returns same game and language but different topic texts`() {
        val game = aGame()
        val topic = aTopic()
        val language = aLanguage()
        val request = aTextBitSearchBySimilarityRequest(game = game, language = language, topic = topic)
        val targetEmbedding = aNormalizedEmbedding()
        val embA = generateVectorWithDistance(targetEmbedding, 0.5)
        val embB = generateVectorWithDistance(targetEmbedding, 0.35)
        val embC = generateVectorWithDistance(targetEmbedding, 0.01)
        val embD = generateVectorWithDistance(targetEmbedding, 0.3)
        val embE = generateVectorWithDistance(targetEmbedding, 0.59)
        val anotherTopic = (Topic.entries - topic).random()
        givenTextBitEmbedding(topic = topic, language = language, game = game, text = "text A", embedding = embA)
        givenTextBitEmbedding(topic = anotherTopic, language = language, game = game, text = "text B", embedding = embB)
        givenTextBitEmbedding(topic = anotherTopic, language = language, game = game, text = "text C", embedding = embC)
        givenTextBitEmbedding(topic = topic, language = language, game = game, text = "text D", embedding = embD)
        givenTextBitEmbedding(topic = topic, language = language, game = game, text = "text E", embedding = embE)
        coEvery { embeddingsProvider.createEmbedding(request.text) } returns targetEmbedding.right()

        val result = given()
            .contentType(ContentType.JSON)
            .body(request.toJsonString())
            .`when`()
            .post("/text-bits/similarity-search")
            .then()
            .statusCode(200)
            .extract()
            .`as`(object : TypeRef<List<Pair<String, Double>>>() {})

        result.map { it.first }.shouldContainExactly("text D", "text A", "text E")
    }

    @Test fun `similaritySearch() Do not returns same game and topic but different language texts`() {
        val game = aGame()
        val topic = aTopic()
        val language = aLanguage()
        val request = aTextBitSearchBySimilarityRequest(game = game, topic = topic, language = language)
        val targetEmbedding = aNormalizedEmbedding()
        val embeddingA = generateVectorWithDistance(targetEmbedding, 0.5)
        val embeddingB = generateVectorWithDistance(targetEmbedding, 0.35)
        val embeddingC = generateVectorWithDistance(targetEmbedding, 0.01)
        val embeddingD = generateVectorWithDistance(targetEmbedding, 0.3)
        val embeddingE = generateVectorWithDistance(targetEmbedding, 0.59)
        val another = language.reversed()
        givenTextBitEmbedding(language = language, topic = topic, game = game, text = "text A", embedding = embeddingA)
        givenTextBitEmbedding(language = another, topic = topic, game = game, text = "text B", embedding = embeddingB)
        givenTextBitEmbedding(language = another, topic = topic, game = game, text = "text C", embedding = embeddingC)
        givenTextBitEmbedding(language = language, topic = topic, game = game, text = "text D", embedding = embeddingD)
        givenTextBitEmbedding(language = language, topic = topic, game = game, text = "text E", embedding = embeddingE)
        coEvery { embeddingsProvider.createEmbedding(request.text) } returns targetEmbedding.right()

        val result = given()
            .contentType(ContentType.JSON)
            .body(request.toJsonString())
            .`when`()
            .post("/text-bits/similarity-search")
            .then()
            .statusCode(200)
            .extract()
            .`as`(object : TypeRef<List<Pair<String, Double>>>() {})

        result.map { it.first }.shouldContainExactly("text D", "text A", "text E")
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private fun givenTextBitEmbedding(
        game: Game = aGame(),
        topic: Topic = aTopic(),
        language: String = aLanguage(),
        text: String,
        embedding: Embedding
    ): UUID {
        val textBit = aTextBit(game = game, topic = topic)
        textBitsRepo.insert(textBit.id, game, topic, createdOn = now)
        val translation = aTranslationEntity(
            textBitId = textBit.id,
            embeddingStatus = EMBEDDED,
            text = text,
            languageCode = language)
        translationsRepo.insert(translation)
        embeddingRepo.insertEmbedding(UUID.randomUUID(), translation.id, embedding, now)
        return translation.id
    }

}
