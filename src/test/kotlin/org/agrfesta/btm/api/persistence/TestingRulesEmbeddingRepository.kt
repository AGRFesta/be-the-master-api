package org.agrfesta.btm.api.persistence

import org.agrfesta.btm.api.persistence.jdbc.repositories.RuleEmbedding
import org.agrfesta.btm.api.persistence.jdbc.repositories.RuleEmbeddingRowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
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
