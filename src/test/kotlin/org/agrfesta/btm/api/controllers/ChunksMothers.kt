package org.agrfesta.btm.api.controllers

import org.agrfesta.btm.api.model.Game
import org.agrfesta.btm.api.model.Chunk
import org.agrfesta.btm.api.model.Topic
import org.agrfesta.btm.api.model.Translation
import org.agrfesta.test.mothers.aRandomUniqueString
import java.util.*

fun aGame() = Game.entries.random()
fun aTopic() = Topic.entries.random()
fun aLanguage() = aRandomUniqueString().take(2)

fun aChunk(
    id: UUID = UUID.randomUUID(),
    game: Game = aGame(),
    topic: Topic = aTopic(),
    translations: Set<Translation> = emptySet()
) = Chunk(id, game, topic, translations)

fun aTranslation(
    text: String = aRandomUniqueString(),
    language: String = aLanguage()
) = Translation(text, language)

fun Translation.toJsonString() = """{"text": "$text", "language": "$language"}"""

fun aChunkCreationRequest(
    game: Game = aGame(),
    translation: Translation = aTranslation(),
    topic: Topic = aTopic(),
    inBatch: Boolean? = null
) = ChunkCreationRequest(game, topic, translation, inBatch ?: false)

fun ChunkCreationRequest.toJsonString() = """
        {
            "game": "$game", 
            "translation": ${translation.toJsonString()}, 
            "topic": "$topic",
            "inBatch": $inBatch
        }
    """.trimIndent()

fun aChunkTranslationsPatchRequest(
    text: String = aRandomUniqueString(),
    language: String = aLanguage(),
    inBatch: Boolean = false
) = ChunkTranslationPatchRequest(text, language, inBatch)

fun ChunkTranslationPatchRequest.toJsonString() = """
        {
            "text": "$text",
            "language": "$language",
            "inBatch": $inBatch
        }
    """.trimIndent()

fun aChunkSearchBySimilarityRequest(
    game: Game = aGame(),
    topic: Topic = aTopic(),
    language: String = aLanguage(),
    text: String = aRandomUniqueString()
) = ChunkSearchBySimilarityRequest(game.name, topic.name, text, language)

fun aChunkSearchBySimilarityRequestJson(
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

fun ChunkSearchBySimilarityRequest.toJsonString() =
    aChunkSearchBySimilarityRequestJson(game, topic, language, text)
