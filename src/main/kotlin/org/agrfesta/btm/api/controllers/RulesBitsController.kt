package org.agrfesta.btm.api.controllers

import arrow.core.Either.Left
import arrow.core.Either.Right
import org.agrfesta.btm.api.model.Game
import org.agrfesta.btm.api.persistence.RulesBitsDao
import org.agrfesta.btm.api.services.RulesService
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.ResponseEntity
import org.springframework.http.ResponseEntity.ok
import org.springframework.http.ResponseEntity.status
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/rules")
class RulesBitsController(
    private val rulesService: RulesService,
    private val rulesBitsDao: RulesBitsDao
) {

    @PostMapping("/bits")
    fun createRuleBit(@RequestBody request: RuleBitCreationRequest): ResponseEntity<Any> {
        when (val insertResult = rulesBitsDao.persist(request.game, request.text)) {
            is Left -> return status(INTERNAL_SERVER_ERROR).body(MessageResponse("Unable to create rule bit!"))
            is Right -> {
                if (!request.inBatch) {
                    val embedResult = rulesService.embedRuleBit(insertResult.value, request.game, request.text)
                    if (embedResult.isLeft()) {
                        return ok().body(MessageResponse(
                            "Rule bit ${insertResult.value} successfully persisted! But embedding creation failed!"))
                    }
                }
                return ok().body(MessageResponse("Rule bit ${insertResult.value} successfully persisted!"))
            }
        }
    }

}

data class RuleBitCreationRequest(
    val game: Game,
    val text: String,
    val inBatch: Boolean = true
)
