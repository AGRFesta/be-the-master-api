package org.agrfesta.btm.api.persistence

import arrow.core.Either
import org.agrfesta.btm.api.model.Embedding
import org.agrfesta.btm.api.model.Game
import org.agrfesta.btm.api.model.PersistenceFailure
import org.springframework.transaction.annotation.Transactional
import java.util.*

interface RulesEmbeddingsDao {

    @Transactional
    fun persist(ruleBitId: UUID, game: Game, embedding: Embedding, text: String): Either<PersistenceFailure, UUID>

}
