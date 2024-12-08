package org.agrfesta.btm.api.persistence.jdbc.repositories

import com.fasterxml.jackson.databind.JsonNode
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@Repository
class CharactersRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {

    fun insertCharacter(partyId: UUID, sheet: JsonNode, createdOn: Instant): UUID {
        val characterId = UUID.randomUUID()

        val sql = """
            INSERT INTO btm.character (id, party_id, sheet, created_on) 
            VALUES (:id, :partyId, :sheet::jsonb, :createdOn)
        """.trimIndent()

        val params = MapSqlParameterSource()
            .addValue("id", characterId)
            .addValue("partyId", partyId)
            .addValue("sheet", sheet.toString())
            .addValue("createdOn", Timestamp.from(createdOn))

        jdbcTemplate.update(sql, params)

        return characterId
    }

}
