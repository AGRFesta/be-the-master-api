package org.agrfesta.btm.api.services

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import org.agrfesta.btm.api.model.BtmFlowFailure
import org.agrfesta.btm.api.model.EmbeddingStatus.EMBEDDED
import org.agrfesta.btm.api.model.Game
import org.agrfesta.btm.api.model.PersistenceFailure
import org.agrfesta.btm.api.model.TextBit
import org.agrfesta.btm.api.model.Topic
import org.agrfesta.btm.api.model.Translation
import org.agrfesta.btm.api.persistence.EmbeddingsDao
import org.agrfesta.btm.api.persistence.TextBitsDao
import org.agrfesta.btm.api.persistence.TranslationsDao
import org.springframework.stereotype.Service
import java.util.*

@Service
class TextBitsService(
    private val textBitsDao: TextBitsDao,
    private val translationsDao: TranslationsDao,
    private val embeddingsDao: EmbeddingsDao
) {

    fun findTextBit(uuid: UUID): TextBit? = textBitsDao.findTextBit(uuid)

    //TODO do we really need this?
    fun persistTranslation(
        translation: Translation,
        textBitId: UUID,
        original: Boolean,
        embedder: Embedder? = null
    ): Either<BtmFlowFailure, UUID> {
        val translationId = translationsDao.persist(textBitId, translation, original)
        return embedder?.let {
            embedder(translation.text).flatMap {
                embeddingsDao.persist(translationId, it).flatMap {
                    translationsDao.setEmbeddingStatus(translationId, EMBEDDED)
                    translationId.right()
                }
            }
        } ?: translationId.right()
    }

    fun createTextBit(game: Game, topic: Topic): Either<BtmFlowFailure, UUID> = try {
        textBitsDao.persist(topic, game).right()
    } catch (e: Exception) {
        PersistenceFailure("Text bit persistence failure!", e).left()
    }

    /**
     * For a specific [TextBit] identified by [textBitId], replace language translation if already exist otherwise
     * simply adds it. Optionally (if [embedder] is not null) creates embedding for the new text.
     *
     * @param textBitId [TextBit] unique identifier.
     * @param language [Translation] language.
     * @param newText new [Translation] text.
     * @param embedder optional embedding function.
     */
    fun replaceTranslation(
        textBitId: UUID,
        language: String,
        newText: String,
        embedder: Embedder? = null
    ): Either<BtmFlowFailure, Unit> {
        val translationId = try {
            translationsDao.addOrReplace(textBitId, language, newText)
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

}
