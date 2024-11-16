package org.agrfesta.btm.api.controllers

import org.agrfesta.btm.api.services.Tokenizer
import org.springframework.http.HttpStatus.OK
import org.springframework.http.ResponseEntity
import org.springframework.http.ResponseEntity.status
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/prompts")
class PromptsController(
    private val tokenizer: Tokenizer
) {

    @PostMapping("/enhance")
    fun enhance(@RequestBody request: PromptRequest): ResponseEntity<Any> {
        return status(OK).body("enhanced prompt")
    }

    @PostMapping("/tokens-count")
    fun tokenCount(@RequestBody request: PromptRequest): ResponseEntity<Any> {
        val count = tokenizer.countTokens(request.prompt)
        return status(OK).body(TokenCountResponse(count))
    }

}

data class PromptRequest(val prompt: String)
data class TokenCountResponse(val count: Int)
