package org.agrfesta.btm.api.services

interface Tokenizer {
    val name: String

    /**
     * Counts tokens in [text].
     *
     * @param text Tokens source text.
     * @return number of tokens in [text].
     */
    fun countTokens(text: String): Int

}
