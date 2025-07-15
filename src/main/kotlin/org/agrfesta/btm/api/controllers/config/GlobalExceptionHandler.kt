package org.agrfesta.btm.api.controllers.config

import com.fasterxml.jackson.databind.exc.InvalidFormatException
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleJsonErrors(ex: HttpMessageNotReadableException): ResponseEntity<MessageResponse> {
        val cause = ex.cause
        val message = when (cause) {
            is InvalidFormatException -> {
                val property = cause.path.firstOrNull()?.fieldName ?: "Unknown property"
                "$property is not valid!"
            }
            is MismatchedInputException -> {
                val property = cause.path.firstOrNull()?.fieldName ?: "Unknown property"
                "$property is missing!"
            }
            else -> "Malformed request"
        }
        return ResponseEntity.badRequest().body(MessageResponse(message))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationExceptions(ex: MethodArgumentNotValidException): ResponseEntity<MessageResponse> {
        val errorMessage = ex.bindingResult.fieldErrors.firstOrNull()?.defaultMessage ?: "Invalid request"
        return ResponseEntity.badRequest().body(MessageResponse(errorMessage))
    }

}
