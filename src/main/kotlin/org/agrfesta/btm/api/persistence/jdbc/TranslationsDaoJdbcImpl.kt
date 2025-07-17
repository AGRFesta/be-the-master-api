package org.agrfesta.btm.api.persistence.jdbc

import org.agrfesta.btm.api.model.EmbeddingStatus
import org.agrfesta.btm.api.model.SupportedLanguage
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
        chunkId: UUID,
        translation: Translation
    ): UUID {
        val now = timeService.nowNoNano()
        val id = randomGenerator.uuid()
        translationsRepo.insert(TranslationEntity(
            id = id,
            chunkId = chunkId,
            text = translation.text,
            language = translation.language,
            embeddingStatus = EmbeddingStatus.UNEMBEDDED,
            createdOn = now
        ))
        return id
    }

    override fun findTranslationByLanguage(chunkId: UUID, language: SupportedLanguage): Translation? =
        translationsRepo.findTranslationByLanguage(chunkId, language)?.let {
            Translation(text = it.text, language = it.language)
        }

    override fun findTranslations(chunkId: UUID): Collection<Translation> =
        translationsRepo.findTranslations(chunkId).map {
            Translation(text = it.text, language = it.language)
        }

    override fun setEmbeddingStatus(translationId: UUID, embeddingStatus: EmbeddingStatus) {
        translationsRepo.update(translationId, embeddingStatus = embeddingStatus)
    }

    override fun addOrReplace(chunkId: UUID, language: SupportedLanguage, newText: String): UUID {
        val translation = translationsRepo.findTranslationByLanguage(chunkId, language)
        return if (translation != null) {
            embeddingRepo.deleteByTranslationId(translation.id)
            translationsRepo.delete(translation.id)
            val uuid = randomGenerator.uuid()
            translationsRepo.insert(
                TranslationEntity(
                    id = uuid,
                    chunkId = chunkId,
                    language = language,
                    text = newText,
                    embeddingStatus = EmbeddingStatus.UNEMBEDDED,
                    createdOn = timeService.nowNoNano()
                )
            )
            uuid
        } else {
            persist(
                chunkId = chunkId,
                translation = Translation(text = newText, language = language)
            )
        }
    }

    override fun delete(translationId: UUID) {
        translationsRepo.delete(translationId)
    }

}
