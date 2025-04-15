package org.agrfesta.btm.api.persistence.jdbc

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.agrfesta.btm.api.model.Game
import org.agrfesta.btm.api.model.PersistenceFailure
import org.agrfesta.btm.api.model.TextBit
import org.agrfesta.btm.api.model.TextBitEmbeddingStatus
import org.agrfesta.btm.api.model.Topic
import org.agrfesta.btm.api.persistence.TextBitsDao
import org.agrfesta.btm.api.persistence.jdbc.repositories.TextBitsRepository
import org.agrfesta.btm.api.services.utils.LoggerDelegate
import org.agrfesta.btm.api.services.utils.RandomGenerator
import org.agrfesta.btm.api.services.utils.TimeService
import org.springframework.stereotype.Service
import java.util.*

@Service
class TextBitsDaoJdbcImpl(
    private val textBitsRepo: TextBitsRepository,
    private val randomGenerator: RandomGenerator,
    private val timeService: TimeService
): TextBitsDao {
    private val logger by LoggerDelegate()

    override fun findTextBit(textBitId: UUID): TextBit? = textBitsRepo.find(textBitId)

    override fun persist(game: Game, text: String, topic: Topic): Either<PersistenceFailure, UUID> {
        val uuid = randomGenerator.uuid()
        try {
            textBitsRepo.insert(
                id = uuid,
                game = game,
                text = text,
                topic = topic,
                createdOn = timeService.nowNoNano()
            )
        } catch (e: Exception) {
            logger.error("Text bit persistence failure!", e)
            PersistenceFailure("Text bit persistence failure!", e).left()
        }
        return uuid.right()
    }

    override fun replaceText(textBitId: UUID, text: String): Either<PersistenceFailure, Unit> = try {
            textBitsRepo.update(
                id = textBitId,
                text = text,
                embeddingStatus = TextBitEmbeddingStatus.UNEMBEDDED,
                updatedOn = timeService.nowNoNano()).right()
        } catch (e: Exception) {
            logger.error("Text bit replace failure!", e)
            PersistenceFailure("Text bit replace failure!", e).left()
        }

    override fun update(
        textBitId: UUID,
        embeddingStatus: TextBitEmbeddingStatus,
        text: String?
    ): Either<PersistenceFailure, Unit> {
        return try {
            textBitsRepo.update(textBitId, timeService.nowNoNano(), TextBitEmbeddingStatus.EMBEDDED).right()
        } catch (e: Exception) {
            logger.error("Text bit update failure!", e)
            PersistenceFailure("Text bit update failure!", e).left()
        }
    }

    override fun delete(textBitId: UUID): Either<PersistenceFailure, Unit> {
        return try {
            textBitsRepo.delete(textBitId)
            Unit.right()
        } catch (e: Exception) {
            logger.error("Text embedding delete failure!", e)
            PersistenceFailure("Text embedding persistence failure!", e).left()
        }
    }

}