package org.agrfesta.btm.api.persistence

import org.agrfesta.btm.api.model.EmbeddingStatus
import org.agrfesta.btm.api.model.TextBit
import org.agrfesta.btm.api.model.Translation
import org.springframework.transaction.annotation.Transactional
import java.util.*

interface TranslationsDao {

    /**
     * Persists a [TextBit]'s [Translation].
     *
     * @param textBitId [TextBit] unique identifier.
     * @param translation [Translation] data.
     * @return [UUID] assigned to persisted [Translation].
     */
    @Transactional
    fun persist(textBitId: UUID, translation: Translation): UUID

    /**
     * Fetches a specific language [TextBit]'s [Translation].
     *
     * @param textBitId [TextBit] unique identifier.
     * @param language [Translation] language.
     * @return found [Translation], null if missing.
     */
    fun findTranslationByLanguage(textBitId: UUID, language: String): Translation?

    /**
     * Fetches all [TextBit]'s [Translation]s.
     *
     * @param textBitId [TextBit] unique identifier.
     * @return found [Translation]s, could be empty.
     */
    fun findTranslations(textBitId: UUID): Collection<Translation>

    /**
     * Updates [Translation]s embedding status.
     *
     * @param translationId [Translation] unique identifier.
     * @param embeddingStatus new [Translation]s embedding status.
     */
    @Transactional
    fun setEmbeddingStatus(translationId: UUID, embeddingStatus: EmbeddingStatus)

    /**
     * Adds a new [Translation] if do not exist already a [Translation] for the same language, otherwise replace it
     * removing previous [Translation] and related embedding.
     *
     * @param textBitId [TextBit] unique identifier.
     * @param language [Translation] language.
     * @param newText new [Translation] text.
     * @return [UUID] assigned to the new persisted [Translation].
     */
    @Transactional
    fun addOrReplace(textBitId: UUID, language: String, newText: String): UUID

    /**
     * Deletes [Translation].
     *
     * @param translationId [Translation] unique identifier.
     */
    @Transactional
    fun delete(translationId: UUID)

}
