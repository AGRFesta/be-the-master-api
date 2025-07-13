package org.agrfesta.btm.api.model

sealed interface BtmFlowFailure
data class BtmConfigurationFailure(val message: String): BtmFlowFailure
data object EmbeddingCreationFailure: BtmFlowFailure, ReplaceTranslationFailure
data object TokenCountFailure: BtmFlowFailure
data class PersistenceFailure(
    val message: String,
    val reason: Exception? = null
): BtmFlowFailure, ReplaceTranslationFailure
data class ValidationFailure(val message:String, val reason: Exception? = null): BtmFlowFailure

sealed interface ReplaceTranslationFailure: PatchChunkFailure

sealed interface PatchChunkFailure
data object MissingChunk: PatchChunkFailure