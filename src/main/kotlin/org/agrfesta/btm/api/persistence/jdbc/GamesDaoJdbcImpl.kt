package org.agrfesta.btm.api.persistence.jdbc

import java.util.*
import org.agrfesta.btm.api.model.Game
import org.agrfesta.btm.api.persistence.GamesDao
import org.agrfesta.btm.api.persistence.jdbc.repositories.GamesRepository
import org.springframework.stereotype.Service

@Service
class GamesDaoJdbcImpl(
    private val gamesRepository: GamesRepository
): GamesDao {

    override fun findGameByName(name: String): Game? = gamesRepository.findByName(name)?.toGame()

    override fun createGame(name: String, description: String?) =
        gamesRepository.insert(UUID.randomUUID(), name, description)

}
