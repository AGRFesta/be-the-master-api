package org.agrfesta.btm.api.services

import arrow.core.Either
import org.agrfesta.btm.api.model.TokenCountFailure

interface Tokenizer {
    val name: String

    /**
     * Counts tokens in [text].
     *
     * @param text Tokens source text.
     * @return number of tokens in [text].
     * @return [Either] containing number of tokens in [text] or [TokenCountFailure] on error.
     */
    fun countTokens(text: String): Either<TokenCountFailure, Int>

}
