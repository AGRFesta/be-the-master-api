package org.agrfesta.btm.api.persistence.jdbc

import org.agrfesta.btm.api.model.Game
import org.agrfesta.btm.api.model.Chunk
import org.agrfesta.btm.api.model.Topic
import org.agrfesta.btm.api.persistence.ChunksDao
import org.agrfesta.btm.api.persistence.jdbc.repositories.ChunksRepository
import org.agrfesta.btm.api.services.utils.RandomGenerator
import org.agrfesta.btm.api.services.utils.TimeService
import org.springframework.stereotype.Service
import java.util.*

@Service
class ChunksDaoJdbcImpl(
    private val chunksRepo: ChunksRepository,
    private val randomGenerator: RandomGenerator,
    private val timeService: TimeService
): ChunksDao {

    override fun findChunk(chunkId: UUID): Chunk? = chunksRepo.find(chunkId)

    override fun persist(topic: Topic, game: Game): UUID {
        val uuid = randomGenerator.uuid()
        chunksRepo.insert(
            id = uuid,
            game = game,
            topic = topic,
            createdOn = timeService.nowNoNano()
        )
        return uuid
    }

    override fun delete(chunkId: UUID) = chunksRepo.delete(chunkId)

}