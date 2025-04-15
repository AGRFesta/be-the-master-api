package org.agrfesta.btm.api.persistence

import org.agrfesta.btm.api.persistence.jdbc.repositories.Embedding
import org.agrfesta.btm.api.persistence.jdbc.repositories.EmbeddingRowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import java.util.*

@Service
class TestingEmbeddingRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {

    fun findById(id: UUID): Embedding? {
        val sql = """SELECT * FROM btm.embeddings WHERE id = :id;"""
        val params = MapSqlParameterSource(mapOf("id" to id))
        return jdbcTemplate.query(sql, params, EmbeddingRowMapper)
                .firstOrNull()
    }

    fun findByTextBitId(id: UUID): Embedding? {
        val sql = """SELECT * FROM btm.embeddings WHERE text_bit_id = :id;"""
        val params = MapSqlParameterSource(mapOf("id" to id))
        return jdbcTemplate.query(sql, params, EmbeddingRowMapper)
            .firstOrNull()
    }

}
