package org.agrfesta.btm.api.persistence.jdbc

import org.agrfesta.btm.api.model.Game
import org.agrfesta.btm.api.persistence.GamesDao
import org.agrfesta.btm.api.persistence.jdbc.repositories.GamesRepository
import org.springframework.stereotype.Service

@Service
class GamesDaoJdbcImpl(
    private val gamesRepository: GamesRepository
): GamesDao {
    override fun findGameByName(name: String): Game? = gamesRepository.findByName(name)?.toGame()
}
