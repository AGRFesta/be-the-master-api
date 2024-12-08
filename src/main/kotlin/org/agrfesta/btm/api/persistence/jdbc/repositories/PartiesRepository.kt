package org.agrfesta.btm.api.persistence.jdbc.repositories

import com.fasterxml.jackson.databind.ObjectMapper
import org.agrfesta.btm.api.model.Game
import org.agrfesta.btm.api.model.Party
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@Repository
class PartiesRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    private val objectMapper: ObjectMapper
) {

    fun insertParty(id: UUID, name: String, game: Game, createdOn: Instant) {
        val sql = """
            INSERT INTO btm.party (id, name, game, created_on) 
            VALUES (:id, :name, CAST(:game AS game_enum), :createdOn)
        """.trimIndent()

        val params = MapSqlParameterSource()
            .addValue("name", name)
            .addValue("id", id)
            .addValue("game", game.name)
            .addValue("createdOn", Timestamp.from(createdOn))

        jdbcTemplate.update(sql, params)
    }

    fun findPartyWithMembers(partyId: UUID): Party? {
        val sql = """
            SELECT p.name, p.game, c.sheet
            FROM btm.party p
            LEFT JOIN btm.character c ON p.id = c.party_id
            WHERE p.id = :party_id
        """.trimIndent()

        val params = MapSqlParameterSource("party_id", partyId)

        val result = jdbcTemplate.query(sql, params) { rs, _ ->
            (rs.getString("name") to rs.getString("game")) to rs.getString("sheet")
        }

        if (result.isEmpty()) {
            return null
        }

        val name = result.first().first.first
        val game = Game.valueOf(result.first().first.second.uppercase(Locale.getDefault()))
        val members = result.mapNotNull { (_, sheet) ->
            sheet?.let { objectMapper.readTree(it) }
        }

        return Party(name = name, game = game, members = members)
    }

}
