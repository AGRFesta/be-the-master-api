package org.agrfesta.btm.api.persistence.jdbc.repositories

import com.pgvector.PGvector
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@Repository
class RulesEmbeddingRepository(
    private val namedParameterJdbcTemplate: NamedParameterJdbcTemplate
) {

    fun insertRuleEmbedding(
        id: UUID,
        ruleBitId: UUID,
        game: String,
        vector: FloatArray,
        text: String,
        createdOn: Instant
    ) {
        val sql = """
        INSERT INTO btm.rules_embeddings (id, rule_bit_id, game, vector, text, created_on)
        VALUES (:id, :ruleBitId, CAST(:game AS game_enum), CAST(:vector AS vector), :text, :createdOn);
        """

        val params = mapOf(
            "id" to id,
            "ruleBitId" to ruleBitId,
            "game" to game,
            "vector" to PGvector(vector),
            "text" to text,
            "createdOn" to Timestamp.from(createdOn)
        )

        namedParameterJdbcTemplate.update(sql, params)
    }

}
