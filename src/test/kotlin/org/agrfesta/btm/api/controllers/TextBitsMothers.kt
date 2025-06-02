package org.agrfesta.btm.api.controllers

import org.agrfesta.btm.api.model.Game
import org.agrfesta.btm.api.model.TextBit
import org.agrfesta.btm.api.model.Topic
import org.agrfesta.btm.api.model.Translation
import org.agrfesta.test.mothers.aRandomUniqueString
import org.agrfesta.test.mothers.anEmbedding
import java.util.*

fun aGame() = Game.entries.random()
fun aTopic() = Topic.entries.random()
fun aLanguage() = aRandomUniqueString().take(2)

fun aTextBit(
    id: UUID = UUID.randomUUID(),
    game: Game = aGame(),
    topic: Topic = aTopic(),
    translations: Set<Translation> = emptySet()
) = TextBit(id, game, topic, translations)

fun aTranslation(
    text: String = aRandomUniqueString(),
    language: String = aLanguage()
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
    language: String = aLanguage(),
    inBatch: Boolean = false
) = TextBitTranslationPatchRequest(text, language, inBatch)

fun TextBitTranslationPatchRequest.toJsonString() = """
        {
            "text": "$text",
            "language": "$language",
            "inBatch": $inBatch
        }
    """.trimIndent()

fun aTextBitSearchBySimilarityRequest(
    game: Game = aGame(),
    topic: Topic = aTopic(),
    language: String = aLanguage(),
    text: String = aRandomUniqueString()
) = TextBitSearchBySimilarityRequest(game.name, topic.name, text, language)

fun aTextBitSearchBySimilarityRequestJson(
    game: String = aGame().name,
    topic: String = aTopic().name,
    language: String = aLanguage(),
    text: String = aRandomUniqueString()
) = """
        {
            "topic": "$topic",
            "text": "$text",
            "language": "$language",
            "game": "$game"
        }
    """.trimIndent()

fun TextBitSearchBySimilarityRequest.toJsonString() =
    aTextBitSearchBySimilarityRequestJson(game, topic, language, text)
