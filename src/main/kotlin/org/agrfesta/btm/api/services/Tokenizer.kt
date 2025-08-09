package org.agrfesta.btm.api.services

import arrow.core.Either
import org.agrfesta.btm.api.model.TokenCountFailure

interface Tokenizer {

    /**
     * Counts tokens in [text].
     *
     * @param text Tokens source text.
     * @param isAQuery is true when [text] is a query.
     * @return number of tokens in [text].
     * @return [Either] containing number of tokens in [text] or [TokenCountFailure] on error.
     */
    suspend fun countTokens(text: String, isAQuery: Boolean): Either<TokenCountFailure, Int>

}
