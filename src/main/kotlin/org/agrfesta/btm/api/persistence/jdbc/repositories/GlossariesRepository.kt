package org.agrfesta.btm.api.persistence.jdbc.repositories

import org.agrfesta.btm.api.model.Game
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant

@Repository
class GlossariesRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {

    fun insertEntry(game: Game, key: String, value: String, createdOn: Instant) {
        val sql = """
            INSERT INTO btm.glossary (term, tran, game, created_on) 
            VALUES (:term, :tran, CAST(:game AS game_enum), :createdOn)
        """.trimIndent()

        val params = MapSqlParameterSource()
            .addValue("term", key)
            .addValue("tran", value)
            .addValue("game", game.name)
            .addValue("createdOn", Timestamp.from(createdOn))

        jdbcTemplate.update(sql, params)
    }

    fun getAllEntriesByGame(game: Game): Map<String, String> {
        val sql = """
            SELECT term, tran
            FROM btm.glossary
            WHERE game = CAST(:game AS game_enum)
        """.trimIndent()

        val params = MapSqlParameterSource("game", game.name)

        return jdbcTemplate.query(sql, params) { rs, _ ->
            rs.getString("term") to rs.getString("tran")
        }.toMap()
    }

    fun deleteEntriesByGame(game: Game) {
        val sql = "DELETE FROM btm.glossary WHERE game = CAST(:game AS game_enum)"
        val params = MapSqlParameterSource("game", game.name)
        jdbcTemplate.update(sql, params)
    }

}
