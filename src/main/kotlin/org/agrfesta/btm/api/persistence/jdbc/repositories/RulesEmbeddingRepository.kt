package org.agrfesta.btm.api.persistence.jdbc.repositories

import com.pgvector.PGvector
import org.agrfesta.btm.api.model.Embedding
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@Repository
class RulesEmbeddingRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
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

        jdbcTemplate.update(sql, params)
    }

    fun getNearestRulesEmbeddings(target: Embedding, game: String): List<RuleEmbedding> {
        val sql = """
            SELECT * FROM btm.rules_embeddings 
            WHERE game = CAST(:game AS game_enum) 
            ORDER BY vector <-> :target LIMIT 5
        """.trimIndent()
        val params = MapSqlParameterSource(mapOf(
            "game" to game,
            "target" to PGvector(target)
        ))
        return jdbcTemplate.query(sql, params, RuleEmbeddingRowMapper)
    }

}

class RuleEmbedding(
    val id: UUID,
    val game: String,
    val vector: FloatArray,
    val text: String,
    val createdOn: Instant
)

object RuleEmbeddingRowMapper: RowMapper<RuleEmbedding> {
    override fun mapRow(rs: ResultSet, rowNum: Int) = RuleEmbedding(
        id = UUID.fromString(rs.getString("id")),
        game = rs.getString("game"),
        vector = (rs.getObject("vector") as PGvector?)?.toArray() ?: error("vector missing"),
        text = rs.getString("text"),
        createdOn = rs.getTimestamp("created_on").toInstant()
    )
}
