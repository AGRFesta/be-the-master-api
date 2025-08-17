package org.agrfesta.btm.api.controllers

import jakarta.validation.Valid
import jakarta.validation.constraints.Pattern
import org.agrfesta.btm.api.controllers.config.MessageResponse
import org.agrfesta.btm.api.persistence.GamesDao
import org.springframework.dao.DataAccessException
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.ResponseEntity
import org.springframework.http.ResponseEntity.internalServerError
import org.springframework.http.ResponseEntity.ok
import org.springframework.http.ResponseEntity.status
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * REST controller for managing Games.
 */
@RestController
@RequestMapping("/games")
class GamesController(
    private val gamesDao: GamesDao
) {

    /**
     * POST /games
     *
     * Creates Game.
     *
     * @param request the [GameCreationRequest] containing name and optional description.
     * @return 200 OK with success message if the game is created,
     *         400 Bad Request if the input validation fails,
     *         500 Internal Server Error if persistence fails during game creation.
     */
    @PostMapping
    fun createGame(@Valid @RequestBody request: GameCreationRequest): ResponseEntity<Any> {
        return try {
            gamesDao.createGame(request.name, request.description)
            ok().body(MessageResponse("game '${request.name}' successfully persisted!"))
        } catch (e: DuplicateKeyException) {
            status(409).body(MessageResponse("game '${request.name}' already exists!"))
        } catch (e: DataAccessException) {
            internalServerError()
                .body(MessageResponse("Unable to create game!"))
        }
    }

}

data class GameCreationRequest(
    val name: String,

    @field:Pattern(
        regexp = "^(?!\\s*$).+",
        message = "description must not be blank!"
    )
    val description: String?
)
