package org.agrfesta.btm.api.persistence

import java.util.*
import org.agrfesta.btm.api.model.Chunk
import org.agrfesta.btm.api.model.Topic
import org.springframework.transaction.annotation.Transactional

interface ChunksDao {

    /**
     * Finds [Chunk] by [chunkId].
     *
     * @param chunkId [Chunk] unique identifier.
     * @return found [Chunk] otherwise null.
     */
    fun findChunk(chunkId: UUID): Chunk?

    /**
     * Persists a [Chunk] by [topic] and game name.
     *
     * @param topic [Chunk] related [Topic].
     * @param gameName The name of the game this [Chunk] belongs to.
     * @return [UUID] assigned to persisted [Chunk].
     */
    @Transactional
    fun persist(topic: Topic, gameName: String): UUID

    /**
     * Deletes a [Chunk] by [chunkId].
     *
     * @param chunkId [Chunk] unique identifier.
     */
    @Transactional
    fun delete(chunkId: UUID)

}
