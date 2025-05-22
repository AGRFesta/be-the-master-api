package org.agrfesta.btm.api.persistence

import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import java.sql.ResultSet
import java.time.Instant
import java.util.*

@Service
class TestingTextBitsRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {

    fun findById(id: UUID): TextBitEntity? {
        val sql = """SELECT * FROM btm.text_bits WHERE id = :id;"""
        val params = MapSqlParameterSource(mapOf("id" to id))
        return jdbcTemplate.query(sql, params, TextBitRowMapper)
            .firstOrNull()
    }

}

object TextBitRowMapper: RowMapper<TextBitEntity> {
    override fun mapRow(rs: ResultSet, rowNum: Int) = TextBitEntity(
        id = UUID.fromString(rs.getString("id")),
        game = rs.getString("game"),
        topic = rs.getString("topic"),
        createdOn = rs.getTimestamp("created_on").toInstant(),
        updatedOn = rs.getTimestamp("updated_on")?.toInstant()
    )
}

class TextBitEntity(
    val id: UUID,
    val game: String,
    val topic: String,
    val createdOn: Instant,
    val updatedOn: Instant?
)