package org.agrfesta.btm.api.controllers

import io.mockk.verify
import org.agrfesta.btm.api.persistence.EmbeddingsDao
import org.agrfesta.btm.api.persistence.TranslationsDao
import org.springframework.boot.test.context.TestComponent

@TestComponent
class TextBitsUnitAsserter(
    private val translationsDao: TranslationsDao,
    private val embeddingsDao: EmbeddingsDao
) {

    fun verifyNoTranslationsPersisted() {
        verify(exactly = 0) { translationsDao.persist(any(), any()) }
    }

    fun verifyNoEmbeddingsPersisted() {
        verify(exactly = 0) { embeddingsDao.persist(any(), any()) }
    }

}
