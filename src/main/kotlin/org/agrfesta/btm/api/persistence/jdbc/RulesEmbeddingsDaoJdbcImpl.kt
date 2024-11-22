package org.agrfesta.btm.api.persistence.jdbc

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.agrfesta.btm.api.model.Embedding
import org.agrfesta.btm.api.model.Game
import org.agrfesta.btm.api.model.PersistenceFailure
import org.agrfesta.btm.api.persistence.RulesEmbeddingsDao
import org.agrfesta.btm.api.persistence.jdbc.repositories.RulesEmbeddingRepository
import org.agrfesta.btm.api.services.utils.LoggerDelegate
import org.agrfesta.btm.api.services.utils.RandomGenerator
import org.agrfesta.btm.api.services.utils.TimeService
import org.springframework.stereotype.Service
import java.util.*

@Service
class RulesEmbeddingsDaoJdbcImpl(
    private val randomGenerator: RandomGenerator,
    private val timeService: TimeService,
    private val rulesEmbeddingRepo: RulesEmbeddingRepository
): RulesEmbeddingsDao {
    private val logger by LoggerDelegate()

    override fun persist(
        ruleBitId: UUID,
        game: Game,
        embedding: Embedding,
        text: String
    ): Either<PersistenceFailure, UUID> {
        val uuid = randomGenerator.uuid()
        return try {
            rulesEmbeddingRepo.insertRuleEmbedding(
                id = uuid,
                ruleBitId = ruleBitId,
                game = game.name,
                vector = embedding,
                text = text,
                createdOn = timeService.nowNoNano()
            )
            uuid.right()
        } catch (e: Exception) {
            logger.error("Rule embedding persistence failure!", e)
            PersistenceFailure("Rule embedding persistence failure!", e).left()
        }
    }

    override fun nearestRules(game: Game, target: Embedding): Either<PersistenceFailure, List<String>> {
        return try {
            val result = rulesEmbeddingRepo.getNearestRulesEmbeddings(target, game.name)
            result.map { it.text }.toList().right()
        } catch (e: Exception) {
            logger.error("Rule embedding persistence failure!", e)
            PersistenceFailure("Rule embedding persistence failure!", e).left()
        }
    }

}
