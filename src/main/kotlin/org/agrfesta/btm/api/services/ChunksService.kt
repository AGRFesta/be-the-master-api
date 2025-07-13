package org.agrfesta.btm.api.services

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import org.agrfesta.btm.api.model.BtmFlowFailure
import org.agrfesta.btm.api.model.Chunk
import org.agrfesta.btm.api.model.EmbeddingStatus.EMBEDDED
import org.agrfesta.btm.api.model.Game
import org.agrfesta.btm.api.model.PersistenceFailure
import org.agrfesta.btm.api.model.ReplaceTranslationFailure
import org.agrfesta.btm.api.model.Topic
import org.agrfesta.btm.api.persistence.ChunksDao
import org.agrfesta.btm.api.persistence.EmbeddingsDao
import org.agrfesta.btm.api.persistence.TranslationsDao
import org.springframework.stereotype.Service
import java.util.*

/**
 * Service layer for managing text chunks, their translations, and embedding-related operations.
 * It acts as a bridge between the web layer and the persistence layer.
 */
@Service
class ChunksService(
    private val chunksDao: ChunksDao,
    private val translationsDao: TranslationsDao,
    private val embeddingsDao: EmbeddingsDao
) {

    companion object {
        /** Default maximum number of embeddings returned in similarity search. */
        const val DEFAULT_EMBEDDINGS_LIMIT = 1_000

        /** Default maximum distance threshold for similarity search results. */
        const val DEFAULT_DISTANCE_LIMIT = 0.3
    }

    /**
     * Retrieves a chunk by its unique identifier.
     *
     * @param uuid the [UUID] of the chunk to retrieve.
     * @return the [Chunk] if found, or null otherwise.
     * @return [Either] containing Chunk or null (if missing) on success,
     *  or [PersistenceFailure] if fetch fails.
     */
    fun findChunk(uuid: UUID): Either<PersistenceFailure, Chunk?> = try {
        chunksDao.findChunk(uuid).right()
    } catch (e: Exception) {
        PersistenceFailure("Chunk fetch failure!", e).left()
    }

    /**
     * Persists a new chunk associated with a given game and topic.
     *
     * @param game the game to associate the chunk with.
     * @param topic the topic to associate the chunk with.
     * @return [Either] containing the UUID of the newly created chunk on success,
     *  or [PersistenceFailure] if saving fails.
     */
    fun createChunk(game: Game, topic: Topic): Either<BtmFlowFailure, UUID> = try {
        chunksDao.persist(topic, game).right()
    } catch (e: Exception) {
        PersistenceFailure("Chunk persistence failure!", e).left()
    }

    /**
     * Replaces or inserts a translation for a given chunk in a specific language.
     * If an [embedder] is provided, the text is embedded and stored.
     *
     * @param chunkId the UUID of the target chunk.
     * @param language the language of the translation.
     * @param newText the translation text to add or replace.
     * @param embedder optional function to create an embedding for the new text.
     * @return [Either] right if the operation succeeds,
     *  or [ReplaceTranslationFailure] if translation or embedding persistence fails.
     */
    fun replaceTranslation(
        chunkId: UUID,
        language: String,
        newText: String,
        embedder: Embedder? = null
    ): Either<ReplaceTranslationFailure, Unit> {
        val translationId = try {
            translationsDao.addOrReplace(chunkId, language, newText)
        } catch (e: Exception) {
            return PersistenceFailure("Unable to patch translations", e).left()
        }
        return embedder?.let  {
            embedder(newText).flatMap {
                embeddingsDao.persist(translationId, it).flatMap {
                    translationsDao.setEmbeddingStatus(translationId, EMBEDDED).right()
                }
            }
        } ?: Unit.right()
    }

    /**
     * Performs a similarity search for the provided text, returning similar chunks
     * filtered by game, topic, and language.
     *
     * @param text the input text to embed and compare.
     * @param game the game to filter the chunks by.
     * @param topic the topic to filter the chunks by.
     * @param language the language to filter the chunks by.
     * @param embedder a function that generates an embedding from the input text.
     * @param embeddingsLimit optional limit for the number of results.
     * @param distanceLimit optional maximum distance for similarity.
     * @return [Either] containing a list of pairs (text, distance) representing similar translations,
     *         or [PersistenceFailure] on error.
     */
    fun searchBySimilarity(
        text: String,
        game: Game,
        topic: Topic,
        language: String,
        embedder: Embedder,
        embeddingsLimit: Int? = null,
        distanceLimit: Double? = null
    ): Either<BtmFlowFailure, List<Pair<String, Double>>> = embedder(text).flatMap {
            try {
                embeddingsDao.searchBySimilarity(it, game, topic, language,
                    embeddingsLimit ?: DEFAULT_EMBEDDINGS_LIMIT,
                    distanceLimit ?: DEFAULT_DISTANCE_LIMIT
                ).right()
            } catch (e: Exception) {
                PersistenceFailure("Unable to fetch embeddings!", e).left()
            }
        }

}
