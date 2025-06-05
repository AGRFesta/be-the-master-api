package org.agrfesta.btm.api.persistence.jdbc.repositories

import org.agrfesta.btm.api.model.Game
import org.agrfesta.btm.api.model.Chunk
import org.agrfesta.btm.api.model.Topic
import org.springframework.dao.DataAccessException
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@Repository
class ChunksRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {

    fun find(id: UUID): Chunk? {
        val sql = """SELECT * FROM btm.chunks WHERE id = :uuid"""
        val params = mapOf("uuid" to id)
        val area: Chunk? = try {
            jdbcTemplate.queryForObject(sql, params, ChunkMapper)
        } catch (e: EmptyResultDataAccessException) {
            null
        }
        return area
    }

    /**
     * @throws DataAccessException
     */
    fun insert(id: UUID, game: Game, topic: Topic, createdOn: Instant) {
        val sql = """
        INSERT INTO btm.chunks (id, game, created_on, topic)
        VALUES (:id, CAST(:game AS game_enum), :createdOn, CAST(:topic AS topic_enum));
        """

        val params = mapOf(
            "id" to id,
            "game" to game.name,
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

object ChunkMapper: RowMapper<Chunk> {
    override fun mapRow(rs: ResultSet, rowNum: Int) = Chunk(
        id = rs.getUuid("id"),
        game = Game.valueOf(rs.getString("game")),
        topic = Topic.valueOf(rs.getString("topic")),
        translations = emptySet()//TODO map
    )
}

fun ResultSet.getUuid(columnLabel: String): UUID = UUID.fromString(getString(columnLabel))
