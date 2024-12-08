package org.agrfesta.btm.api.persistence

import arrow.core.Either
import com.fasterxml.jackson.databind.JsonNode
import org.agrfesta.btm.api.model.Game
import org.agrfesta.btm.api.model.Party
import org.agrfesta.btm.api.model.PersistenceFailure
import org.springframework.transaction.annotation.Transactional
import java.util.*

interface PartiesDao {

    @Transactional
    fun persist(name: String, game: Game, members: Collection<JsonNode>): Either<PersistenceFailure, UUID>

    fun getParty(id: UUID): Either<PersistenceFailure, Party>

}
