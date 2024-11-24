package org.agrfesta.btm.api.model

sealed interface BtmFlowFailure
data object EmbeddingCreationFailure: BtmFlowFailure
data class PersistenceFailure(
    val message: String,
    val reason: Exception? = null
): BtmFlowFailure
