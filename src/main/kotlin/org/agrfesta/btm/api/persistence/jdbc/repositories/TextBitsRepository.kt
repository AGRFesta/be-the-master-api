package org.agrfesta.btm.api.persistence.jdbc.repositories

import org.agrfesta.btm.api.model.Game
import org.agrfesta.btm.api.model.TextBit
import org.agrfesta.btm.api.model.TextBitEmbeddingStatus
import org.agrfesta.btm.api.model.Topic
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@Repository
class TextBitsRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {

    fun find(id: UUID): TextBit? {
        val sql = """SELECT * FROM btm.text_bits WHERE id = :uuid"""
        val params = mapOf("uuid" to id)
        val area: TextBit? = try {
            jdbcTemplate.queryForObject(sql, params, TextBitMapper)
        } catch (e: EmptyResultDataAccessException) {
            null
        }
        return area
    }

    fun insert(id: UUID, game: Game, text: String, topic: Topic, createdOn: Instant) {
        val sql = """
        INSERT INTO btm.text_bits (id, game, embedding_status, text, created_on, topic)
        VALUES (:id, CAST(:game AS game_enum), 'UNEMBEDDED', :text, :createdOn, CAST(:topic AS topic_enum));
        """

        val params = mapOf(
            "id" to id,
            "game" to game.name,
            "text" to text,
            "topic" to topic.name,
            "createdOn" to Timestamp.from(createdOn)
        )

        jdbcTemplate.update(sql, params)
    }

    fun update(id: UUID, updatedOn: Instant, embeddingStatus: TextBitEmbeddingStatus, text: String? = null) {
        val sql = StringBuilder("""
        UPDATE btm.text_bits
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
            DELETE FROM btm.text_bits
            WHERE id = :uuid;
        """
        jdbcTemplate.update(sql, mapOf("uuid" to uuid))
    }

}

object TextBitMapper: RowMapper<TextBit> {
    override fun mapRow(rs: ResultSet, rowNum: Int) = TextBit(
        id = rs.getUuid("id"),
        game = Game.valueOf(rs.getString("game")),
        text = rs.getString("text")
    )
}

fun ResultSet.getUuid(columnLabel: String): UUID = UUID.fromString(getString(columnLabel))
