package org.agrfesta.btm.api.controllers

import org.agrfesta.btm.api.model.Game
import org.agrfesta.btm.api.model.TextBit
import org.agrfesta.btm.api.model.Topic
import org.agrfesta.btm.api.model.Translation
import org.agrfesta.test.mothers.aRandomUniqueString
import java.util.*

fun aGame() = Game.entries.random()
fun aTopic() = Topic.entries.random()

fun aTextBit(
    id: UUID = UUID.randomUUID(),
    game: Game = aGame(),
    topic: Topic = aTopic(),
    translations: Set<Translation> = emptySet()
) = TextBit(id, game, topic, translations)

fun aTranslation(
    text: String = aRandomUniqueString(),
    language: String = "it"
) = Translation(text, language)

fun Translation.toJsonString() = """{"text": "$text", "language": "$language"}"""

fun aTextBitCreationRequest(
    game: Game = aGame(),
    translation: Translation = aTranslation(),
    topic: Topic = aTopic(),
    inBatch: Boolean? = null
) = TextBitCreationRequest(game, topic, translation, inBatch ?: false)

fun TextBitCreationRequest.toJsonString() = """
        {
            "game": "$game", 
            "translation": ${translation.toJsonString()}, 
            "topic": "$topic",
            "inBatch": $inBatch
        }
    """.trimIndent()

fun aTextBitTranslationsPatchRequest(
    text: String = aRandomUniqueString(),
    language: String = aRandomUniqueString(),
    inBatch: Boolean = false
) = TextBitTranslationPatchRequest(text, language, inBatch)

fun TextBitTranslationPatchRequest.toJsonString() = """
        {
            "text": "$text",
            "language": "$language",
            "inBatch": $inBatch
        }
    """.trimIndent()
