package org.agrfesta.btm.api.services

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.right
import kotlinx.coroutines.runBlocking
import org.agrfesta.btm.api.model.BtmFlowFailure
import org.agrfesta.btm.api.model.Game
import org.agrfesta.btm.api.model.RuleBit
import org.agrfesta.btm.api.model.RuleBitsEmbeddingStatus.EMBEDDED
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

    fun findRuleBit(uuid: UUID): RuleBit? = rulesBitsDao.findRuleBit(uuid)

    @Transactional
    fun createRuleBit(game: Game, text: String): Either<BtmFlowFailure, UUID> = rulesBitsDao.persist(game, text)

    @Transactional
    fun replaceRuleBit(ruleId: UUID, game: Game, text: String, inBatch: Boolean): Either<BtmFlowFailure, Unit> =
        rulesEmbeddingsDao.deleteByRuleId(ruleId).flatMap {
            rulesBitsDao.replaceText(ruleId, text).flatMap {
                if (!inBatch) {
                    embedRuleBit(ruleId, game, text)
                } else Unit.right()
            }
        }

    @Transactional
    fun embedRuleBit(ruleId: UUID, game: Game, text: String): Either<BtmFlowFailure, Unit> =
        runBlocking { embeddingsService.createEmbedding(text) }.flatMap {
            rulesEmbeddingsDao.persist(ruleId, game, it, text).flatMap {
                rulesBitsDao.update(ruleId, EMBEDDED)
            }
        }

}
