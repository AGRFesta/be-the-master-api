package org.agrfesta.btm.api.controllers

import io.mockk.every
import io.mockk.verify
import org.agrfesta.btm.api.model.TextBit
import org.agrfesta.btm.api.model.Translation
import org.agrfesta.btm.api.persistence.EmbeddingsDao
import org.agrfesta.btm.api.persistence.TextBitsDao
import org.agrfesta.btm.api.persistence.TranslationsDao
import org.springframework.boot.test.context.TestComponent
import java.util.*

@TestComponent
class TextBitsUnitAsserter(
    private val textBitsDao: TextBitsDao,
    private val translationsDao: TranslationsDao,
    private val embeddingsDao: EmbeddingsDao
) {
    private val textBitId = UUID.randomUUID()

    fun givenTextBitCreation(textBit: TextBit) {
        every { textBitsDao.persist(textBit.topic, textBit.game) } returns textBitId
        every { translationsDao.persist(textBitId, textBit.original, original = true) } returns UUID.randomUUID()
        textBit.translations.forEach {
            every { translationsDao.persist(textBitId, it, original = false) } returns UUID.randomUUID()
        }
    }

    fun verifyNoTranslationsPersisted() {
        verify(exactly = 0) { translationsDao.persist(any(), any(), any()) }
    }

    fun verifyNoEmbeddingsPersisted() {
        verify(exactly = 0) { embeddingsDao.persist(any(), any()) }
    }

    fun verifyTranslationPersistence(translation: Translation) {
        verify { translationsDao.persist(textBitId, translation, any()) }
    }

    fun verifyNothingPersisted() {
        verify(exactly = 0) { textBitsDao.persist(any(), any()) }
        verifyNoTranslationsPersisted()
        verifyNoEmbeddingsPersisted()
    }

}
