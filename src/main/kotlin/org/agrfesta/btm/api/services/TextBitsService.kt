package org.agrfesta.btm.api.services

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import org.agrfesta.btm.api.model.BtmFlowFailure
import org.agrfesta.btm.api.model.EmbeddingStatus.EMBEDDED
import org.agrfesta.btm.api.model.Game
import org.agrfesta.btm.api.model.PersistenceFailure
import org.agrfesta.btm.api.model.Chunk
import org.agrfesta.btm.api.model.Topic
import org.agrfesta.btm.api.model.Translation
import org.agrfesta.btm.api.persistence.EmbeddingsDao
import org.agrfesta.btm.api.persistence.ChunksDao
import org.agrfesta.btm.api.persistence.TranslationsDao
import org.springframework.stereotype.Service
import java.util.*

@Service
class ChunksService(
    private val chunksDao: ChunksDao,
    private val translationsDao: TranslationsDao,
    private val embeddingsDao: EmbeddingsDao
) {

    fun findChunk(uuid: UUID): Chunk? = chunksDao.findChunk(uuid)

    //TODO do we really need this?
    fun persistTranslation(
        translation: Translation,
        chunkId: UUID,
        embedder: Embedder? = null
    ): Either<BtmFlowFailure, UUID> {
        val translationId = translationsDao.persist(chunkId, translation)
        return embedder?.let {
            embedder(translation.text).flatMap {
                embeddingsDao.persist(translationId, it).flatMap {
                    translationsDao.setEmbeddingStatus(translationId, EMBEDDED)
                    translationId.right()
                }
            }
        } ?: translationId.right()
    }

    fun createChunk(game: Game, topic: Topic): Either<BtmFlowFailure, UUID> = try {
        chunksDao.persist(topic, game).right()
    } catch (e: Exception) {
        PersistenceFailure("Chunk persistence failure!", e).left()
    }

    /**
     * For a specific [Chunk] identified by [chunkId], replace language translation if already exist otherwise
     * simply adds it. Optionally (if [embedder] is not null) creates embedding for the new text.
     *
     * @param chunkId [Chunk] unique identifier.
     * @param language [Translation] language.
     * @param newText new [Translation] text.
     * @param embedder optional embedding function.
     */
    fun replaceTranslation(
        chunkId: UUID,
        language: String,
        newText: String,
        embedder: Embedder? = null
    ): Either<BtmFlowFailure, Unit> {
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

    fun searchBySimilarity(
        text: String,
        game: Game,
        topic: Topic,
        language: String,
        embedder: Embedder
    ): Either<BtmFlowFailure, List<Pair<String, Double>>> = embedder(text).flatMap {
            try {
                embeddingsDao.searchBySimilarity(it, game, topic, language).right()
            } catch (e: Exception) {
                PersistenceFailure("unable to fetch embeddings", e).left()
            }
        }

}
