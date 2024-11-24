package org.agrfesta.btm.api.persistence

import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import java.sql.ResultSet
import java.time.Instant
import java.util.*

@Service
class TestingRulesBitsRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {

    fun findById(id: UUID): RuleBit? {
        val sql = """SELECT * FROM btm.rules_bits WHERE id = :id;"""
        val params = MapSqlParameterSource(mapOf("id" to id))
        return jdbcTemplate.query(sql, params, RuleBitRowMapper)
            .firstOrNull()
    }

}

object RuleBitRowMapper: RowMapper<RuleBit> {
    override fun mapRow(rs: ResultSet, rowNum: Int) = RuleBit(
        id = UUID.fromString(rs.getString("id")),
        game = rs.getString("game"),
        text = rs.getString("text"),
        embeddingStatus = rs.getString("embedding_status"),
        createdOn = rs.getTimestamp("created_on").toInstant(),
        updatedOn = rs.getTimestamp("updated_on")?.toInstant()
    )
}

class RuleBit(
    val id: UUID,
    val game: String,
    val text: String,
    val embeddingStatus: String,
    val createdOn: Instant,
    val updatedOn: Instant?
)