package org.agrfesta.btm.api.persistence.jdbc.repositories

import java.sql.Timestamp
import java.time.Instant
import java.util.*
import org.agrfesta.btm.api.model.Topic
import org.agrfesta.btm.api.persistence.jdbc.entities.ChunkEntity
import org.springframework.dao.DataAccessException
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class ChunksRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {

    /**
     * Finds a chunk by its unique identifier.
     *
     * @param id The unique identifier of the chunk (UUID).
     * @return The matching [ChunkEntity], or `null` if no chunk exists with the given ID.
     *
     * @throws DataAccessException if a database access error occurs.
     */
    fun find(id: UUID): ChunkEntity? {
        val sql = """
            SELECT id, game_id, topic, created_on, updated_on
            FROM btm.chunks
            WHERE id = :uuid
        """
        val params = mapOf("uuid" to id)

        return try {
            jdbcTemplate.queryForObject(sql, params) { rs, _ ->
                ChunkEntity(
                    id = UUID.fromString(rs.getString("id")),
                    gameId = UUID.fromString(rs.getString("game_id")),
                    topic = Topic.valueOf(rs.getString("topic")),
                    createdOn = rs.getTimestamp("created_on").toInstant(),
                    updatedOn = rs.getTimestamp("updated_on")?.toInstant()
                )
            }
        } catch (e: EmptyResultDataAccessException) {
            null
        }
    }

    /**
     * Inserts a new chunk into the database.
     *
     * This method expects the caller to provide a valid [gameId].
     *
     * @param id Unique identifier for the chunk (UUID).
     * @param gameId The UUID of the game this chunk belongs to.
     * @param topic The topic classification of the chunk (e.g., RULE, LORE).
     * @param createdOn The timestamp when the chunk is created.
     *
     * @throws DataAccessException if a database access error occurs,
     *         for example if the provided `gameId` does not exist or a constraint is violated.
     */
    fun insert(id: UUID, gameId: UUID, topic: Topic, createdOn: Instant) {
        val sql = """
            INSERT INTO btm.chunks (id, game_id, created_on, topic)
            VALUES (:id, :gameId, :createdOn, CAST(:topic AS topic_enum));
        """

        val params = mapOf(
            "id" to id,
            "gameId" to gameId,
            "topic" to topic.name,
            "createdOn" to Timestamp.from(createdOn)
        )

        jdbcTemplate.update(sql, params)
    }

    fun delete(uuid: UUID) {
        val sql = """
            DELETE FROM btm.chunks
            WHERE id = :uuid;
        """
        jdbcTemplate.update(sql, mapOf("uuid" to uuid))
    }

}

//object ChunkMapper: RowMapper<Chunk> {
//    override fun mapRow(rs: ResultSet, rowNum: Int) = Chunk(
//        id = rs.getUuid("id"),
//        game = Game.valueOf(rs.getString("game")),
//        topic = Topic.valueOf(rs.getString("topic")),
//        translations = emptySet()//TODO map
//    )
//}

//fun ResultSet.getUuid(columnLabel: String): UUID = UUID.fromString(getString(columnLabel))
