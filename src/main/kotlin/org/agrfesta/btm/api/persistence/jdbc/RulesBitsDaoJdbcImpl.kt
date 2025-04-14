package org.agrfesta.btm.api.persistence.jdbc

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.agrfesta.btm.api.model.Game
import org.agrfesta.btm.api.model.PersistenceFailure
import org.agrfesta.btm.api.model.RuleBit
import org.agrfesta.btm.api.model.RuleBitsEmbeddingStatus
import org.agrfesta.btm.api.persistence.RulesBitsDao
import org.agrfesta.btm.api.persistence.jdbc.repositories.RulesBitsRepository
import org.agrfesta.btm.api.services.utils.LoggerDelegate
import org.agrfesta.btm.api.services.utils.RandomGenerator
import org.agrfesta.btm.api.services.utils.TimeService
import org.springframework.stereotype.Service
import java.util.*

@Service
class RulesBitsDaoJdbcImpl(
    private val rulesBitsRepo: RulesBitsRepository,
    private val randomGenerator: RandomGenerator,
    private val timeService: TimeService
): RulesBitsDao {
    private val logger by LoggerDelegate()

    override fun findRuleBit(id: UUID): RuleBit? = rulesBitsRepo.find(id)

    override fun persist(game: Game, text: String): Either<PersistenceFailure, UUID> {
        val uuid = randomGenerator.uuid()
        try {
            rulesBitsRepo.insert(
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

    override fun replaceText(ruleId: UUID, text: String): Either<PersistenceFailure, Unit> = try {
            rulesBitsRepo.update(
                id = ruleId,
                text = text,
                embeddingStatus = RuleBitsEmbeddingStatus.UNEMBEDDED,
                updatedOn = timeService.nowNoNano()).right()
        } catch (e: Exception) {
            logger.error("Rule bit replace failure!", e)
            PersistenceFailure("Rule bit replace failure!", e).left()
        }

    override fun update(
        id: UUID,
        embeddingStatus: RuleBitsEmbeddingStatus,
        text: String?
    ): Either<PersistenceFailure, Unit> {
        return try {
            rulesBitsRepo.update(id, timeService.nowNoNano(), RuleBitsEmbeddingStatus.EMBEDDED).right()
        } catch (e: Exception) {
            logger.error("Rule bit update failure!", e)
            PersistenceFailure("Rule bit update failure!", e).left()
        }
    }

    override fun delete(id: UUID): Either<PersistenceFailure, Unit> {
        return try {
            rulesBitsRepo.delete(id)
            Unit.right()
        } catch (e: Exception) {
            logger.error("Rule embedding delete failure!", e)
            PersistenceFailure("Rule embedding persistence failure!", e).left()
        }
    }

}