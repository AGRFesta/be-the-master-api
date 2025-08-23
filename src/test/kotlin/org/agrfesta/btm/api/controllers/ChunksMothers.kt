package org.agrfesta.btm.api.controllers

import org.agrfesta.btm.api.model.Game
import org.agrfesta.btm.api.model.Chunk
import org.agrfesta.btm.api.model.SupportedLanguage
import org.agrfesta.btm.api.model.Topic
import org.agrfesta.btm.api.model.Translation
import org.agrfesta.test.mothers.aRandomUniqueString
import java.util.*
import kotlin.collections.Collection

fun aTopic() = Topic.entries.random()
fun aLanguage() = SupportedLanguage.entries.random()
fun aSupportedLanguage() = SupportedLanguage.entries.random()

fun aChunk(
    id: UUID = UUID.randomUUID(),
    game: Game = aGame(),
    topic: Topic = aTopic(),
    translations: Set<Translation> = emptySet()
) = Chunk(id, game, topic, translations)

fun aChunkContentJson(
    text: String? = aRandomUniqueString(),
    nonSemanticaPart: String? = null
): String {
    val properties = buildList {
        nonSemanticaPart?.let { add(""""nonSemanticaPart": "$nonSemanticaPart"""") }
        text?.let { add(""""text": "$text"""") }
    }
    return properties.joinToString(
        separator = ",\n    ",
        prefix = "{\n    ",
        postfix = "\n}"
    )
}

fun aChunksCreationRequestJson(
    game: String? = aGame().name,
    topic: String? = aTopic().name,
    language: String? = aLanguage().name,
    chunksContent: List<String>? = List(3) { aChunkContentJson() },
    embed: Boolean? = true
): String {
    val properties = buildList {
        game?.let { add(""""game": "$it"""") }
        topic?.let { add(""""topic": "$topic"""") }
        language?.let { add(""""language": "$language"""") }
        chunksContent?.let { add(""""chunksContent": ${chunksContent.toJsonObjectArray()}""") }
        embed?.let { add(""""embed": $embed""") }
    }

    return properties.joinToString(
        separator = ",\n    ",
        prefix = "{\n    ",
        postfix = "\n}"
    )
}

fun aChunkTranslationsPatchRequestJson(
    text: String? = aRandomUniqueString(),
    language: String? = aLanguage().name,
    inBatch: Boolean? = true
): String {
    val properties = buildList {
        text?.let { add(""""text": "$it"""") }
        language?.let { add(""""language": "$language"""") }
        inBatch?.let { add(""""embed": $inBatch""") }
    }

    return properties.joinToString(
        separator = ",\n    ",
        prefix = "{\n    ",
        postfix = "\n}"
    )
}

fun Collection<String>.toJsonStringArray(): String = joinToString(
    prefix = "[",
    postfix = "]",
    separator = ","
) { "\"${it}\"" }

fun Collection<String>.toJsonObjectArray(): String = joinToString(
    prefix = "[",
    postfix = "]",
    separator = ","
) { "${it}" }

fun aChunkTranslationsPatchRequest(
    text: String = aRandomUniqueString(),
    language: SupportedLanguage = aLanguage(),
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
    game: String = aRandomUniqueString(),
    topic: Topic = aTopic(),
    language: SupportedLanguage = aLanguage(),
    text: String = aRandomUniqueString(),
    embeddingsLimit: Int? = null,
    distanceLimit: Double? = null
) = ChunkSearchBySimilarityRequest(game, topic, text, language, embeddingsLimit, distanceLimit)

fun aChunkSearchBySimilarityRequestJson(
    game: String? = aGame().name,
    topic: String? = aTopic().name,
    language: String? = aLanguage().name,
    text: String? = aRandomUniqueString(),
    embeddingsLimit: Int? = null,
    distanceLimit: Double? = null
): String {
    val properties = buildList {
        game?.let { add(""""game": "$it"""") }
        topic?.let { add(""""topic": "$topic"""") }
        language?.let { add(""""language": "$language"""") }
        text?.let { add(""""text": "$text"""") }
        embeddingsLimit?.let { add(""""embeddingsLimit": $embeddingsLimit""") }
        distanceLimit?.let { add(""""distanceLimit": $distanceLimit""") }
    }

    return properties.joinToString(
        separator = ",\n    ",
        prefix = "{\n    ",
        postfix = "\n}"
    )
}

fun ChunkSearchBySimilarityRequest.toJsonString() =
    aChunkSearchBySimilarityRequestJson(game, topic.name, language.name, text)
