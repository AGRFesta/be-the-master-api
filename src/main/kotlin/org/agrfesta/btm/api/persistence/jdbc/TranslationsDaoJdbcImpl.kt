package org.agrfesta.btm.api.persistence.jdbc

import org.agrfesta.btm.api.model.EmbeddingStatus
import org.agrfesta.btm.api.model.Translation
import org.agrfesta.btm.api.persistence.TranslationsDao
import org.agrfesta.btm.api.persistence.jdbc.entities.TranslationEntity
import org.agrfesta.btm.api.persistence.jdbc.repositories.EmbeddingRepository
import org.agrfesta.btm.api.persistence.jdbc.repositories.TranslationsRepository
import org.agrfesta.btm.api.services.utils.RandomGenerator
import org.agrfesta.btm.api.services.utils.TimeService
import org.springframework.stereotype.Service
import java.util.*

@Service
class TranslationsDaoJdbcImpl(
    private val randomGenerator: RandomGenerator,
    private val timeService: TimeService,
    private val translationsRepo: TranslationsRepository,
    private val embeddingRepo: EmbeddingRepository
): TranslationsDao {

    override fun persist(
        textBitId: UUID,
        translation: Translation,
        original: Boolean
    ): UUID {
        val now = timeService.nowNoNano()
        val id = randomGenerator.uuid()
        translationsRepo.insert(TranslationEntity(
            id = id,
            textBitId = textBitId,
            text = translation.text,
            languageCode = translation.language,
            original = original,
            embeddingStatus = EmbeddingStatus.UNEMBEDDED,
            createdOn = now
        ))
        return id
    }

    override fun findTranslationByLanguage(textBitId: UUID, language: String): Translation? =
        translationsRepo.findTranslationByLanguage(textBitId, language)?.let {
            Translation(text = it.text, language = it.languageCode)
        }

    override fun findTranslations(textBitId: UUID): Collection<Translation> =
        translationsRepo.findTranslations(textBitId).map {
            Translation(text = it.text, language = it.languageCode)
        }

    override fun setEmbeddingStatus(translationId: UUID, embeddingStatus: EmbeddingStatus) {
        translationsRepo.update(translationId, embeddingStatus = embeddingStatus)
    }

    override fun addOrReplace(textBitId: UUID, language: String, newText: String): UUID {
        val translation = translationsRepo.findTranslationByLanguage(textBitId, language)
        return if (translation != null) {
            embeddingRepo.deleteByTranslationId(translation.id)
            translationsRepo.delete(translation.id)
            val uuid = randomGenerator.uuid()
            translationsRepo.insert(
                TranslationEntity(
                    id = uuid,
                    textBitId = textBitId,
                    languageCode = language,
                    original = translation.original,
                    text = newText,
                    embeddingStatus = EmbeddingStatus.UNEMBEDDED,
                    createdOn = timeService.nowNoNano()
                )
            )
            uuid
        } else {
            persist(
                textBitId = textBitId,
                translation = Translation(text = newText, language = language),
                original = false
            )
        }
    }

    override fun delete(translationId: UUID) {
        translationsRepo.delete(translationId)
    }

}
