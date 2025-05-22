package org.agrfesta.btm.api.persistence.jdbc

import org.agrfesta.btm.api.model.Game
import org.agrfesta.btm.api.model.TextBit
import org.agrfesta.btm.api.model.Topic
import org.agrfesta.btm.api.persistence.TextBitsDao
import org.agrfesta.btm.api.persistence.jdbc.repositories.TextBitsRepository
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

    override fun findTextBit(textBitId: UUID): TextBit? = textBitsRepo.find(textBitId)

    override fun persist(topic: Topic, game: Game): UUID {
        val uuid = randomGenerator.uuid()
        textBitsRepo.insert(
            id = uuid,
            game = game,
            topic = topic,
            createdOn = timeService.nowNoNano()
        )
        return uuid
    }

    override fun delete(textBitId: UUID) = textBitsRepo.delete(textBitId)

}