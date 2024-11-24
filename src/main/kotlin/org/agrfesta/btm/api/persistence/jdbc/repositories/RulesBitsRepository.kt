package org.agrfesta.btm.api.persistence.jdbc.repositories

import org.agrfesta.btm.api.model.Game
import org.agrfesta.btm.api.model.RuleBitEmbeddingStatus
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@Repository
class RulesBitsRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {

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

    fun update(id: UUID, updatedOn: Instant, embeddingStatus: RuleBitEmbeddingStatus, text: String? = null) {
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



}
