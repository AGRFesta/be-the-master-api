package org.agrfesta.btm.api.persistence

import com.pgvector.PGvector
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import java.sql.ResultSet
import java.time.Instant
import java.util.*

@Service
class TestingRulesEmbeddingRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {

    fun findById(id: UUID): RuleEmbedding? {
        val sql = """SELECT * FROM btm.rules_embeddings WHERE id = :id;"""
        val params = MapSqlParameterSource(mapOf("id" to id))
        return jdbcTemplate.query(sql, params, RuleEmbeddingRowMapper)
                .firstOrNull()
    }

    fun findByRuleBitId(id: UUID): RuleEmbedding? {
        val sql = """SELECT * FROM btm.rules_embeddings WHERE rule_bit_id = :id;"""
        val params = MapSqlParameterSource(mapOf("id" to id))
        return jdbcTemplate.query(sql, params, RuleEmbeddingRowMapper)
            .firstOrNull()
    }

}

object RuleEmbeddingRowMapper: RowMapper<RuleEmbedding> {
    override fun mapRow(rs: ResultSet, rowNum: Int) = RuleEmbedding(
        id = UUID.fromString(rs.getString("id")),
        game = rs.getString("game"),
        vector = (rs.getObject("vector") as PGvector?)?.toArray() ?: error("vector missing"),
        text = rs.getString("text"),
        createdOn = rs.getTimestamp("created_on").toInstant()
    )
}

class RuleEmbedding(
    val id: UUID,
    val game: String,
    val vector: FloatArray,
    val text: String,
    val createdOn: Instant
)
