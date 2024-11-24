package org.agrfesta.btm.api.services

import arrow.core.Either
import arrow.core.flatMap
import kotlinx.coroutines.runBlocking
import org.agrfesta.btm.api.model.BtmFlowFailure
import org.agrfesta.btm.api.model.Game
import org.agrfesta.btm.api.model.RuleBitEmbeddingStatus.EMBEDDED
import org.agrfesta.btm.api.persistence.RulesBitsDao
import org.agrfesta.btm.api.persistence.RulesEmbeddingsDao
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class RulesService(
    private val rulesBitsDao: RulesBitsDao,
    private val rulesEmbeddingsDao: RulesEmbeddingsDao,
    private val embeddingsService: EmbeddingsService
) {

    @Transactional
    fun embedRuleBit(ruleId: UUID, game: Game, text: String): Either<BtmFlowFailure, Unit> =
        runBlocking { embeddingsService.createEmbedding(text) }.flatMap {
            rulesEmbeddingsDao.persist(ruleId, game, it, text).flatMap {
                rulesBitsDao.update(ruleId, EMBEDDED)
            }
        }

}
