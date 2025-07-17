package org.agrfesta.btm.api.persistence

import org.agrfesta.btm.api.model.EmbeddingStatus
import org.agrfesta.btm.api.model.Chunk
import org.agrfesta.btm.api.model.SupportedLanguage
import org.agrfesta.btm.api.model.Translation
import org.springframework.transaction.annotation.Transactional
import java.util.*

interface TranslationsDao {

    /**
     * Persists a [Chunk]'s [Translation].
     *
     * @param chunkId [Chunk] unique identifier.
     * @param translation [Translation] data.
     * @return [UUID] assigned to persisted [Translation].
     */
    @Transactional
    fun persist(chunkId: UUID, translation: Translation): UUID

    /**
     * Fetches a specific language [Chunk]'s [Translation].
     *
     * @param chunkId [Chunk] unique identifier.
     * @param language [SupportedLanguage] language.
     * @return found [Translation], null if missing.
     */
    fun findTranslationByLanguage(chunkId: UUID, language: SupportedLanguage): Translation?

    /**
     * Fetches all [Chunk]'s [Translation]s.
     *
     * @param chunkId [Chunk] unique identifier.
     * @return found [Translation]s, could be empty.
     */
    fun findTranslations(chunkId: UUID): Collection<Translation>

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
     * @param chunkId [Chunk] unique identifier.
     * @param language [Translation] language.
     * @param newText new [Translation] text.
     * @return [UUID] assigned to the new persisted [Translation].
     */
    @Transactional
    fun addOrReplace(chunkId: UUID, language: SupportedLanguage, newText: String): UUID

    /**
     * Deletes [Translation].
     *
     * @param translationId [Translation] unique identifier.
     */
    @Transactional
    fun delete(translationId: UUID)

}
