package org.agrfesta.btm.api.persistence.jdbc

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.agrfesta.btm.api.model.Game
import org.agrfesta.btm.api.model.PersistenceFailure
import org.agrfesta.btm.api.model.RuleBitEmbeddingStatus
import org.agrfesta.btm.api.persistence.RulesBitsDao
import org.agrfesta.btm.api.persistence.jdbc.repositories.RulesBitsRepository
import org.agrfesta.btm.api.services.utils.LoggerDelegate
import org.agrfesta.btm.api.services.utils.RandomGenerator
import org.agrfesta.btm.api.services.utils.TimeService
import org.springframework.stereotype.Service
import java.util.*

@Service
class RulesBitsDaoJdbcImpl(
    private val rulesBitsRepository: RulesBitsRepository,
    private val randomGenerator: RandomGenerator,
    private val timeService: TimeService
): RulesBitsDao {
    private val logger by LoggerDelegate()

    override fun persist(game: Game, text: String): Either<PersistenceFailure, UUID> {
        val uuid = randomGenerator.uuid()
        try {
            rulesBitsRepository.insert(
                id = uuid,
                game = game,
                text = text,
                createdOn = timeService.nowNoNano()
            )
        } catch (e: Exception) {
            logger.error("Rule bit persistence failure!", e)
            PersistenceFailure("Rule bit persistence failure!", e).left()
        }
        return uuid.right()
    }

    override fun update(
        id: UUID,
        embeddingStatus: RuleBitEmbeddingStatus,
        text: String?
    ): Either<PersistenceFailure, Unit> {
        return try {
            rulesBitsRepository.update(id, timeService.nowNoNano(), RuleBitEmbeddingStatus.EMBEDDED).right()
        } catch (e: Exception) {
            logger.error("Rule bit update failure!", e)
            PersistenceFailure("Rule bit update failure!", e).left()
        }
    }
}