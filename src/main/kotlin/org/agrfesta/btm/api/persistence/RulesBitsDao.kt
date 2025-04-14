package org.agrfesta.btm.api.persistence

import arrow.core.Either
import org.agrfesta.btm.api.model.Game
import org.agrfesta.btm.api.model.PersistenceFailure
import org.agrfesta.btm.api.model.RuleBit
import org.agrfesta.btm.api.model.RuleBitsEmbeddingStatus
import org.springframework.transaction.annotation.Transactional
import java.util.*

interface RulesBitsDao {

    fun findRuleBit(id: UUID): RuleBit?

    @Transactional
    fun persist(game: Game, text: String): Either<PersistenceFailure, UUID>

    @Transactional
    fun replaceText(ruleId: UUID, text: String): Either<PersistenceFailure, Unit>

    @Transactional
    fun update(
        id: UUID,
        embeddingStatus: RuleBitsEmbeddingStatus,
        text: String? = null
    ): Either<PersistenceFailure, Unit>

    @Transactional
    fun delete(id: UUID): Either<PersistenceFailure, Unit>

}
