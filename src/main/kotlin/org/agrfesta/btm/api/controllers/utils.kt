package org.agrfesta.btm.api.controllers

import arrow.core.Either
import org.agrfesta.btm.api.model.BtmConfigurationFailure
import org.agrfesta.btm.api.model.BtmFlowFailure
import org.agrfesta.btm.api.model.EmbeddingCreationFailure
import org.agrfesta.btm.api.model.PersistenceFailure
import org.agrfesta.btm.api.model.TokenCountFailure
import org.agrfesta.btm.api.model.ValidationFailure
import org.springframework.http.ResponseEntity
import org.springframework.http.ResponseEntity.badRequest
import org.springframework.http.ResponseEntity.internalServerError
import org.springframework.http.ResponseEntity.ok

//TODO we should reconsider this generic flow, probably better define specific flows, like in the case of Replace Translation flow.
val btmFlowFailureHandler: (BtmFlowFailure) -> ResponseEntity<Any> = {
    when(it) {
        is EmbeddingCreationFailure -> internalServerError()
            .body(MessageResponse("Unable to create target embedding!"))
        is PersistenceFailure -> internalServerError()
            .body(MessageResponse(it.message))
        is ValidationFailure -> badRequest().body(MessageResponse(it.message))
        is BtmConfigurationFailure -> internalServerError()
            .body(MessageResponse(it.message))
        is TokenCountFailure -> TODO()
    }
}

fun Either<BtmFlowFailure, Any>.toResponseEntity(): ResponseEntity<Any> {
    return fold(
        ifLeft = btmFlowFailureHandler,
        ifRight = { ok().body(it) }
    )
}
