package org.agrfesta.btm.api.persistence

import org.agrfesta.btm.api.model.Game
import org.agrfesta.btm.api.model.Chunk
import org.agrfesta.btm.api.model.Topic
import org.springframework.transaction.annotation.Transactional
import java.util.*

interface ChunksDao {

    /**
     * Finds [Chunk] by [chunkId].
     *
     * @param chunkId [Chunk] unique identifier.
     * @return found [Chunk] otherwise null.
     */
    fun findChunk(chunkId: UUID): Chunk?

    /**
     * Persists a [Chunk] by [topic] and [game].
     *
     * @param topic [Chunk] related [Topic].
     * @param game [Chunk] related [Game].
     * @return [UUID] assigned to persisted [Chunk].
     */
    @Transactional
    fun persist(topic: Topic, game: Game): UUID

    /**
     * Deletes a [Chunk] by [chunkId].
     *
     * @param chunkId [Chunk] unique identifier.
     */
    @Transactional
    fun delete(chunkId: UUID)

}
