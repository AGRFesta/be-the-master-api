package org.agrfesta.btm.api.persistence.jdbc

import java.util.*
import org.agrfesta.btm.api.model.Chunk
import org.agrfesta.btm.api.model.Topic
import org.agrfesta.btm.api.persistence.ChunksDao
import org.agrfesta.btm.api.persistence.jdbc.repositories.ChunksRepository
import org.agrfesta.btm.api.persistence.jdbc.repositories.GamesRepository
import org.agrfesta.btm.api.services.utils.RandomGenerator
import org.agrfesta.btm.api.services.utils.TimeService
import org.springframework.stereotype.Service

@Service
class ChunksDaoJdbcImpl(
    private val chunksRepo: ChunksRepository,
    private val gamesRepo: GamesRepository,
    private val randomGenerator: RandomGenerator,
    private val timeService: TimeService
): ChunksDao {

    override fun findChunk(chunkId: UUID): Chunk? = chunksRepo.find(chunkId)?.let {
        Chunk(
            id = it.id,
            game = gamesRepo.getById(it.gameId).toGame(),
            topic = it.topic,
            translations = emptySet()
        )
    }

    override fun persist(topic: Topic, gameName: String): UUID {
        val uuid = randomGenerator.uuid()
        chunksRepo.insert(
            id = uuid,
            gameId = gamesRepo.getByName(gameName).id,
            topic = topic,
            createdOn = timeService.nowNoNano()
        )
        return uuid
    }

    override fun delete(chunkId: UUID) = chunksRepo.delete(chunkId)

}