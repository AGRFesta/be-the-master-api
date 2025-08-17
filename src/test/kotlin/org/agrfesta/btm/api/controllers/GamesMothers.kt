package org.agrfesta.btm.api.controllers

import org.agrfesta.btm.api.model.Game
import org.agrfesta.test.mothers.aRandomUniqueString

fun aGame(
    name: String = aRandomUniqueString(),
    description: String? = null
) = Game(name, description)

fun aGameCreationRequestJson(
    name: String? = aRandomUniqueString(),
    description: String? = null
): String {
    val properties = buildList {
        name?.let { add(""""name": "$it"""") }
        description?.let { add(""""description": "$description"""") }
    }
    return properties.joinToString(
        separator = ",\n    ",
        prefix = "{\n    ",
        postfix = "\n}"
    )
}