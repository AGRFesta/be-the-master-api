package org.agrfesta.btm.api.controllers

import org.agrfesta.test.mothers.aRandomUniqueString

fun aBasicPromptEnhanceRequestJson(
    game: String? = aGame().name,
    topic: String? = aTopic().name,
    language: String? = aSupportedLanguage().name,
    prompt: String? = aRandomUniqueString(),
    maxTokens: Int? = null
): String {
    val properties = buildList {
        game?.let { add(""""game": "$it"""") }
        topic?.let { add(""""topic": "$topic"""") }
        language?.let { add(""""language": "$language"""") }
        prompt?.let { add(""""prompt": "$prompt"""") }
        maxTokens?.let { add(""""maxTokens": $maxTokens""") }
    }

    return properties.joinToString(
        separator = ",\n    ",
        prefix = "{\n    ",
        postfix = "\n}"
    )
}