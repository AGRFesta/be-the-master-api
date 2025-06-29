package org.agrfesta.btm.api.model

sealed interface BtmFlowFailure
data class BtmConfigurationFailure(val message: String): BtmFlowFailure
data object EmbeddingCreationFailure: BtmFlowFailure
data class PersistenceFailure(
    val message: String,
    val reason: Exception? = null
): BtmFlowFailure
data class ValidationFailure(val message:String, val reason: Exception? = null): BtmFlowFailure
