package org.agrfesta.btm.api.controllers

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import org.agrfesta.btm.api.controllers.PromptsController.Companion.DEFAULT_MAX_TOKENS
import org.agrfesta.btm.api.model.Game
import org.agrfesta.btm.api.model.SupportedLanguage
import org.agrfesta.btm.api.model.Topic
import org.agrfesta.btm.api.model.ValidationFailure
import org.agrfesta.btm.api.services.ChunksService.Companion.DEFAULT_DISTANCE_LIMIT
import org.agrfesta.btm.api.services.ChunksService.Companion.DEFAULT_EMBEDDINGS_LIMIT

data class ValidChunksCreationRequest(
    val game: Game,
    val topic: Topic,
    val language: String,
    val texts: Set<String>
)

data class ValidChunkSearchBySimilarityRequest(
    val game: Game,
    val topic: Topic,
    val text: String,
    val language: String,
    val embeddingsLimit: Int,
    val distanceLimit: Double
)

data class ValidBasicPromptEnhanceRequest(
    val game: Game,
    val topic: Topic,
    val prompt: String,
    val language: SupportedLanguage,
    val maxTokens: Int
)

fun ChunksCreationRequest.validate(): Either<ValidationFailure, ValidChunksCreationRequest> =
    texts.validateTranslationTexts().flatMap {
        validTexts -> language.validateLanguage().flatMap {
            validLanguage -> game.validateGame().flatMap {
                validGame -> topic.validateTopic().flatMap {
                    validTopic ->
                        ValidChunksCreationRequest(
                            game = validGame,
                            topic = validTopic,
                            language = validLanguage,
                            texts = validTexts
                        ).right()
                }
            }
        }
    }

fun ChunkSearchBySimilarityRequest.validate(): Either<ValidationFailure, ValidChunkSearchBySimilarityRequest> =
    text.validateText().flatMap {
        validText -> language.validateLanguage().flatMap {
            validLanguage -> game.validateGame().flatMap {
                validGame -> topic.validateTopic().flatMap {
                    validTopic -> embeddingsLimit.validateEmbeddingsLimit().flatMap {
                        embeddingsLimit -> distanceLimit.validateDistanceLimit().flatMap {
                            distanceLimit -> ValidChunkSearchBySimilarityRequest(
                                validGame, validTopic, validText, validLanguage, embeddingsLimit, distanceLimit).right()
                        }
                    }
                }
            }
        }
    }

fun BasicPromptEnhanceRequest.validate(): Either<ValidationFailure, ValidBasicPromptEnhanceRequest> =
    prompt.validatePrompt().flatMap { validPrompt ->
        language.validateSupportedLanguage().flatMap { validLanguage ->
            game.validateGame().flatMap { validGame ->
                topic.validateTopic().flatMap { validTopic ->
                    maxTokens.validateMaxTokens().flatMap { validMaxTokens ->
                        ValidBasicPromptEnhanceRequest(
                            validGame, validTopic, validPrompt, validLanguage, validMaxTokens).right()
                    }
                }
            }
        }
    }


private fun Collection<String>?.validateTranslationTexts(): Either<ValidationFailure, Set<String>> {
    val texts = this?.filter { it.isNotBlank() }?.toSet()
    return if (texts.isNullOrEmpty()) ValidationFailure("No chunks to create!").left()
    else texts.right()
}

private fun Int?.validateEmbeddingsLimit(): Either<ValidationFailure, Int> {
    val limit = this ?: DEFAULT_EMBEDDINGS_LIMIT
    return if (limit > 0) limit.right()
    else ValidationFailure("'embeddingsLimit' must be a positive Int!").left()
}

private fun Int?.validateMaxTokens(): Either<ValidationFailure, Int> {
    val limit = this ?: DEFAULT_MAX_TOKENS
    return if (limit > 0) limit.right()
    else ValidationFailure("'maxTokens' must be a positive Int!").left()
}

private fun Double?.validateDistanceLimit(): Either<ValidationFailure, Double> {
    val limit = this ?: DEFAULT_DISTANCE_LIMIT
    return if ((limit > 0.0) && (limit < 2.0)) limit.right()
    else ValidationFailure("'distanceLimit' must be in (0.0 ; 2.0)!").left()
}

private fun String?.validateLanguage(): Either<ValidationFailure, String> = if (isNullOrBlank() || length!=2) {
        ValidationFailure("Language must not be blank and two charters long!").left()
    } else this.right()

private fun String?.validateSupportedLanguage(): Either<ValidationFailure, SupportedLanguage> =
    if (this.isNullOrBlank() ) {
        ValidationFailure("Language is missing!").left()
    } else {
        try {
            SupportedLanguage.valueOf(this).right()
        } catch (e: IllegalArgumentException) {
            ValidationFailure("Language is not valid!", e).left()
        }
    }

private fun String.validateText(): Either<ValidationFailure, String> = if (isNullOrBlank()) {
    ValidationFailure("Text must not be blank!").left()
} else this.right()

private fun String?.validatePrompt(): Either<ValidationFailure, String> = if (isNullOrBlank()) {
    ValidationFailure("Prompt must not be blank!").left()
} else this.right()

private fun String?.validateGame(): Either<ValidationFailure, Game> =
    if (this.isNullOrBlank() ) {
        ValidationFailure("Game is missing!").left()
    } else {
        try {
            Game.valueOf(this).right()
        } catch (e: IllegalArgumentException) {
            ValidationFailure("Game is not valid!", e).left()
        }
    }

private fun String?.validateTopic(): Either<ValidationFailure, Topic> =
    if (this.isNullOrBlank() ) {
        ValidationFailure("Topic is missing!").left()
    } else {
        try {
            Topic.valueOf(this).right()
        } catch (e: IllegalArgumentException) {
            ValidationFailure("Topic is not valid!", e).left()
        }
    }
