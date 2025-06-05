package org.agrfesta.btm.api.persistence

import arrow.core.Either
import org.agrfesta.btm.api.model.Embedding
import org.agrfesta.btm.api.model.Game
import org.agrfesta.btm.api.model.PersistenceFailure
import org.agrfesta.btm.api.model.Topic
import org.springframework.transaction.annotation.Transactional
import java.util.*

interface EmbeddingsDao {

    @Transactional
    fun persist(translationId: UUID, embedding: Embedding): Either<PersistenceFailure, UUID>

    @Deprecated("use searchBySimilarity instead")
    fun nearestChunks(game: Game, embedding: Embedding): Either<PersistenceFailure, List<String>>

    fun searchBySimilarity(target: Embedding, game: Game, topic: Topic, language: String): List<Pair<String, Double>>

    @Transactional
    fun deleteByTranslationId(uuid: UUID): Either<PersistenceFailure, Unit>

}
