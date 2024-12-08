package org.agrfesta.btm.api.persistence.jdbc

import arrow.core.Either
import arrow.core.right
import com.fasterxml.jackson.databind.JsonNode
import org.agrfesta.btm.api.model.Game
import org.agrfesta.btm.api.model.Party
import org.agrfesta.btm.api.model.PersistenceFailure
import org.agrfesta.btm.api.persistence.PartiesDao
import org.agrfesta.btm.api.persistence.jdbc.repositories.CharactersRepository
import org.agrfesta.btm.api.persistence.jdbc.repositories.PartiesRepository
import org.agrfesta.btm.api.services.utils.RandomGenerator
import org.agrfesta.btm.api.services.utils.TimeService
import org.springframework.stereotype.Service
import java.util.*

@Service
class PartiesDaoJdbcImpl(
    private val partiesRepository: PartiesRepository,
    private val charactersRepository: CharactersRepository,
    private val randomGenerator: RandomGenerator,
    private val timeService: TimeService
): PartiesDao {

    override fun persist(name: String, game: Game, members: Collection<JsonNode>): Either<PersistenceFailure, UUID> {
        val partyId = randomGenerator.uuid()
        val now = timeService.nowNoNano()
        partiesRepository.insertParty(partyId, name, game, now)
        members.forEach {
            charactersRepository.insertCharacter(partyId, it, now)
        }
        return partyId.right()
    }

    override fun getParty(id: UUID): Either<PersistenceFailure, Party> {
        TODO("Not yet implemented")
    }

}
