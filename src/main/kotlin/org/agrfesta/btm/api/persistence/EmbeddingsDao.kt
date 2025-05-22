package org.agrfesta.btm.api.persistence

import arrow.core.Either
import org.agrfesta.btm.api.model.Embedding
import org.agrfesta.btm.api.model.Game
import org.agrfesta.btm.api.model.PersistenceFailure
import org.springframework.transaction.annotation.Transactional
import java.util.*

interface EmbeddingsDao {

    @Transactional
    fun persist(translationId: UUID, embedding: Embedding): Either<PersistenceFailure, UUID>

    fun nearestTextBits(game: Game, embedding: Embedding): Either<PersistenceFailure, List<String>>

    @Transactional
    fun deleteByTranslationId(uuid: UUID): Either<PersistenceFailure, Unit>

}
