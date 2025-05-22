package org.agrfesta.btm.api.persistence.jdbc

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.agrfesta.btm.api.model.Embedding
import org.agrfesta.btm.api.model.Game
import org.agrfesta.btm.api.model.PersistenceFailure
import org.agrfesta.btm.api.persistence.EmbeddingsDao
import org.agrfesta.btm.api.persistence.jdbc.repositories.EmbeddingRepository
import org.agrfesta.btm.api.services.utils.LoggerDelegate
import org.agrfesta.btm.api.services.utils.RandomGenerator
import org.agrfesta.btm.api.services.utils.TimeService
import org.springframework.stereotype.Service
import java.util.*

@Service
class EmbeddingsDaoJdbcImpl(
    private val randomGenerator: RandomGenerator,
    private val timeService: TimeService,
    private val embeddingRepo: EmbeddingRepository
): EmbeddingsDao {
    private val logger by LoggerDelegate()

    override fun persist(
        translationId: UUID,
        embedding: Embedding
    ): Either<PersistenceFailure, UUID> {
        val uuid = randomGenerator.uuid()
        return try {
            embeddingRepo.insertEmbedding(
                id = uuid,
                translationId = translationId,
                vector = embedding,
                createdOn = timeService.nowNoNano()
            )
            uuid.right()
        } catch (e: Exception) {
            logger.error("Text embedding persistence failure!", e)
            PersistenceFailure("Text embedding persistence failure!", e).left()
        }
    }

    override fun nearestTextBits(game: Game, target: Embedding): Either<PersistenceFailure, List<String>> {
        return try {
            TODO("must be re-implemented")
//            val result = embeddingRepo.getNearestEmbeddings(target, game.name)
//            result.map { it.text }.toList().right()
        } catch (e: Exception) {
            logger.error("Text embedding persistence failure!", e)
            PersistenceFailure("Text embedding persistence failure!", e).left()
        }
    }

    override fun deleteByTranslationId(uuid: UUID): Either<PersistenceFailure, Unit> {
        return try {
            embeddingRepo.deleteByTranslationId(uuid)
            Unit.right()
        } catch (e: Exception) {
            logger.error("Text embedding delete failure!", e)
            PersistenceFailure("Text embedding persistence failure!", e).left()
        }
    }

}
