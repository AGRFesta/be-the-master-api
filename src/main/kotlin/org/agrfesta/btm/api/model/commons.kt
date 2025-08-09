package org.agrfesta.btm.api.model

/**
 * Base type for all failures that can occur during a BTM (Be The Master) flow.
 *
 * Implementations of this interface represent specific categories of errors
 * that may arise during configuration, embedding creation, persistence,
 * token counting, or validation steps.
 */
sealed interface BtmFlowFailure

/**
 * Indicates a configuration-related failure in the BTM flow.
 *
 * @property message A human-readable description of the configuration issue.
 */
data class BtmConfigurationFailure(val message: String) : BtmFlowFailure

/**
 * Indicates a failure during the embedding creation process.
 *
 * This failure type also implements [ReplaceTranslationFailure], meaning
 * it can be triggered during translation replacement flows as part of a patch operation.
 *
 * @property message A human-readable description of the embedding creation error.
 */
data class EmbeddingCreationFailure(
    val message: String
) : BtmFlowFailure, ReplaceTranslationFailure

/**
 * Indicates a failure in counting tokens from a given text input.
 *
 * @property message A human-readable description of the token counting error.
 */
data class TokenCountFailure(
    val message: String
) : BtmFlowFailure

/**
 * Indicates a failure while persisting data (e.g., to a database).
 *
 * This failure type also implements [ReplaceTranslationFailure], meaning
 * it can be triggered during translation replacement flows as part of a patch operation.
 *
 * @property message A human-readable description of the persistence error.
 * @property reason  The underlying exception that caused the failure, if available.
 */
data class PersistenceFailure(
    val message: String,
    val reason: Exception? = null
) : BtmFlowFailure, ReplaceTranslationFailure

/**
 * Indicates a failure during data validation.
 *
 * @property message A human-readable description of the validation error.
 * @property reason  The underlying exception that caused the failure, if available.
 */
data class ValidationFailure(
    val message: String,
    val reason: Exception? = null
) : BtmFlowFailure

/**
 * Marker interface for failures that can occur during translation replacement
 * operations in patch flows.
 *
 * Extends [PatchChunkFailure], meaning these failures are part of the broader
 * category of chunk patching issues.
 */
sealed interface ReplaceTranslationFailure : PatchChunkFailure

/**
 * Marker interface for failures that can occur during patch operations on chunks.
 */
sealed interface PatchChunkFailure

/**
 * Indicates that a required chunk is missing during a patch operation.
 *
 * This is a singleton object because it does not carry additional data.
 */
data object MissingChunk : PatchChunkFailure
