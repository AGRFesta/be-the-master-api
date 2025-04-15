package org.agrfesta.btm.api.persistence

import arrow.core.Either
import org.agrfesta.btm.api.model.Game
import org.agrfesta.btm.api.model.PersistenceFailure
import org.agrfesta.btm.api.model.TextBit
import org.agrfesta.btm.api.model.TextBitEmbeddingStatus
import org.agrfesta.btm.api.model.Topic
import org.springframework.transaction.annotation.Transactional
import java.util.*

interface TextBitsDao {

    fun findTextBit(textBitId: UUID): TextBit?

    @Transactional
    fun persist(game: Game, text: String, topic: Topic): Either<PersistenceFailure, UUID>

    @Transactional
    fun replaceText(textBitId: UUID, text: String): Either<PersistenceFailure, Unit>

    @Transactional
    fun update(
        textBitId: UUID,
        embeddingStatus: TextBitEmbeddingStatus,
        text: String? = null
    ): Either<PersistenceFailure, Unit>

    @Transactional
    fun delete(textBitId: UUID): Either<PersistenceFailure, Unit>

}
