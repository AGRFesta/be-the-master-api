package org.agrfesta.btm.api.persistence.jdbc.repositories

import org.agrfesta.btm.api.model.Game
import org.agrfesta.btm.api.model.RuleBit
import org.agrfesta.btm.api.model.RuleBitsEmbeddingStatus
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@Repository
class RulesBitsRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {

    fun find(id: UUID): RuleBit? {
        val sql = """SELECT * FROM btm.rules_bits WHERE id = :uuid"""
        val params = mapOf("uuid" to id)
        val area: RuleBit? = try {
            jdbcTemplate.queryForObject(sql, params, RuleBitMapper)
        } catch (e: EmptyResultDataAccessException) {
            null
        }
        return area
    }

    fun insert(id: UUID, game: Game, text: String, createdOn: Instant) {
        val sql = """
        INSERT INTO btm.rules_bits (id, game, embedding_status, text, created_on)
        VALUES (:id, CAST(:game AS game_enum), 'UNEMBEDDED', :text, :createdOn);
        """

        val params = mapOf(
            "id" to id,
            "game" to game.name,
            "text" to text,
            "createdOn" to Timestamp.from(createdOn)
        )

        jdbcTemplate.update(sql, params)
    }

    fun update(id: UUID, updatedOn: Instant, embeddingStatus: RuleBitsEmbeddingStatus, text: String? = null) {
        val sql = StringBuilder("""
        UPDATE btm.rules_bits
        SET embedding_status = CAST(:embeddingStatus AS embedding_status_enum),
            updated_on = :updatedOn
        """)

        val params = mutableMapOf(
            "id" to id,
            "embeddingStatus" to embeddingStatus.name,
            "updatedOn" to Timestamp.from(updatedOn)
        )

        text?.let {
            sql.append(", text = :text")
            params["text"] = it
        }

        sql.append(" WHERE id = :id")

        jdbcTemplate.update(sql.toString(), params)
    }

    fun delete(uuid: UUID) {
        val sql = """
            DELETE FROM btm.rules_bits
            WHERE id = :uuid;
        """
        jdbcTemplate.update(sql, mapOf("uuid" to uuid))
    }

}

object RuleBitMapper: RowMapper<RuleBit> {
    override fun mapRow(rs: ResultSet, rowNum: Int) = RuleBit(
        id = rs.getUuid("id"),
        game = Game.valueOf(rs.getString("game")),
        text = rs.getString("text")
    )
}

fun ResultSet.getUuid(columnLabel: String): UUID = UUID.fromString(getString(columnLabel))
