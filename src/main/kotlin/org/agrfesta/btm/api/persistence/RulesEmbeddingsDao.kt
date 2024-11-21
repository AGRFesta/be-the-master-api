package org.agrfesta.btm.api.persistence

import arrow.core.Either
import org.agrfesta.btm.api.model.Embedding
import org.agrfesta.btm.api.model.Game
import java.util.*

interface RulesEmbeddingsDao {

    fun persist(game: Game, embedding: Embedding, text: String): Either<PersistenceFailure, UUID>

}

object PersistenceFailure
