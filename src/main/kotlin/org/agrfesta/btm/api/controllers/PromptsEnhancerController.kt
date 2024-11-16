package org.agrfesta.btm.api.controllers

import org.springframework.http.HttpStatus.OK
import org.springframework.http.ResponseEntity
import org.springframework.http.ResponseEntity.status
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/prompts/enhance")
class PromptsEnhancerController() {

    @PostMapping
    fun create(@RequestBody request: PromptEnhanceRequest): ResponseEntity<Any> {
        return status(OK).body("enhanced prompt")
    }

}

data class PromptEnhanceRequest(val prompt: String)

