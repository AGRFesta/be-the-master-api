package org.agrfesta.btm.api.controllers

import arrow.core.Either.Left
import arrow.core.Either.Right
import com.fasterxml.jackson.databind.JsonNode
import org.agrfesta.btm.api.model.Game
import org.agrfesta.btm.api.persistence.PartiesDao
import org.springframework.http.HttpStatus.CREATED
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.ResponseEntity
import org.springframework.http.ResponseEntity.status
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/parties")
class PartiesController(
    private val partiesDao: PartiesDao
) {

    @PostMapping
    fun createParty(@RequestBody request: PartyCreationRequest): ResponseEntity<Any> =
        when (val result = partiesDao.persist(
            name = request.name,
            game = request.game,
            members = request.sheets)) {
            is Left -> status(INTERNAL_SERVER_ERROR).body(MessageResponse("Unable to create party!"))
            is Right -> status(CREATED).body(MessageResponse("Party ${result.value} successfully persisted!"))
        }

}

class PartyCreationRequest(
    val name: String,
    val game: Game,
    val sheets: Collection<JsonNode>
)
