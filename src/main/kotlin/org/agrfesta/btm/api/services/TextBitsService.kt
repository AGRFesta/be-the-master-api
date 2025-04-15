package org.agrfesta.btm.api.services

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.right
import kotlinx.coroutines.runBlocking
import org.agrfesta.btm.api.model.BtmFlowFailure
import org.agrfesta.btm.api.model.Game
import org.agrfesta.btm.api.model.TextBit
import org.agrfesta.btm.api.model.TextBitEmbeddingStatus.EMBEDDED
import org.agrfesta.btm.api.model.Topic
import org.agrfesta.btm.api.persistence.TextBitsDao
import org.agrfesta.btm.api.persistence.EmbeddingsDao
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class TextBitsService(
    private val textBitsDao: TextBitsDao,
    private val embeddingsDao: EmbeddingsDao,
    private val embeddingsService: EmbeddingsService
) {

    fun findTextBit(uuid: UUID): TextBit? = textBitsDao.findTextBit(uuid)

    @Transactional
    fun createTextBit(game: Game, text: String, topic: Topic): Either<BtmFlowFailure, UUID> =
        textBitsDao.persist(game, text, topic)

    @Transactional
    fun replaceTextBit(textBitId: UUID, game: Game, text: String, inBatch: Boolean): Either<BtmFlowFailure, Unit> =
        embeddingsDao.deleteByTextBitId(textBitId).flatMap {
            textBitsDao.replaceText(textBitId, text).flatMap {
                if (!inBatch) {
                    embedTextBit(textBitId, game, text)
                } else Unit.right()
            }
        }

    @Transactional
    fun embedTextBit(textBitId: UUID, game: Game, text: String): Either<BtmFlowFailure, Unit> =
        runBlocking { embeddingsService.createEmbedding(text) }.flatMap {
            embeddingsDao.persist(textBitId, game, it, text).flatMap {
                textBitsDao.update(textBitId, EMBEDDED)
            }
        }

}
