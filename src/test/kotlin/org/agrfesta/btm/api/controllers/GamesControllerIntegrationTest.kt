package org.agrfesta.btm.api.controllers

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.agrfesta.btm.api.controllers.config.MessageResponse
import org.agrfesta.btm.api.persistence.jdbc.entities.aGameEntity
import org.agrfesta.btm.api.persistence.jdbc.repositories.GamesRepository
import org.agrfesta.test.mothers.aRandomUniqueString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.junit.jupiter.Container

class GamesControllerIntegrationTest(
    @Autowired private val gamesRepo: GamesRepository
): AbstractIntegrationTest() {

    companion object {
        @Container
        @ServiceConnection
        val postgres = createPostgresContainer()
    }

    @Test
    fun `createGame() Creates a new game with description`() {
        val game = aGame(description = aRandomUniqueString())
        game.description.shouldNotBeNull()
        val request = aGameCreationRequestJson(
            name = game.name,
            description = game.description
        )

        val result = given()
            .contentType(ContentType.JSON)
            .body(request)
            .`when`()
            .post("/games")
            .then()
            .statusCode(200)
            .extract()
            .`as`(MessageResponse::class.java)

        result.message shouldBe "game '${game.name}' successfully persisted!"
        val persistedGame = gamesRepo.findByName(game.name)
        persistedGame.shouldNotBeNull()
        persistedGame.name shouldBe game.name
        persistedGame.description shouldBe game.description
    }

    @Test
    fun `createGame() Creates a new game without description`() {
        val name = aRandomUniqueString()
        val request = aGameCreationRequestJson(
            name = name,
            description = null
        )

        val result = given()
            .contentType(ContentType.JSON)
            .body(request)
            .`when`()
            .post("/games")
            .then()
            .statusCode(200)
            .extract()
            .`as`(MessageResponse::class.java)

        result.message shouldBe "game '$name' successfully persisted!"
        val persistedGame = gamesRepo.findByName(name)
        persistedGame.shouldNotBeNull()
        persistedGame.name shouldBe name
        persistedGame.description.shouldBeNull()
    }

    @Test
    fun `createGame() Returns 409 when a game with the same name already exists`() {
        val game = aGameEntity()
        gamesRepo.insert(game.id, game.name, game.description)
        val request = aGameCreationRequestJson(
            name = game.name,
            description = game.description
        )

        val result = given()
            .contentType(ContentType.JSON)
            .body(request)
            .`when`()
            .post("/games")
            .then()
            .statusCode(409)
            .extract()
            .`as`(MessageResponse::class.java)

        result.message shouldBe "game '${game.name}' already exists!"
    }

}
