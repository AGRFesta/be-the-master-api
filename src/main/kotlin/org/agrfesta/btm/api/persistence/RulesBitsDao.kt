package org.agrfesta.btm.api.persistence

import arrow.core.Either
import org.agrfesta.btm.api.model.Game
import org.agrfesta.btm.api.model.PersistenceFailure
import org.agrfesta.btm.api.model.RuleBitEmbeddingStatus
import org.springframework.transaction.annotation.Transactional
import java.util.*

interface RulesBitsDao {

    @Transactional
    fun persist(game: Game, text: String): Either<PersistenceFailure, UUID>

    @Transactional
    fun update(
        id: UUID,
        embeddingStatus: RuleBitEmbeddingStatus,
        text: String? = null
    ): Either<PersistenceFailure, Unit>

}
