package org.agrfesta.btm.api.persistence.jdbc.repositories

import com.pgvector.PGvector
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@Repository
class EmbeddingRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {

    fun insertEmbedding(
        id: UUID,
        textBitId: UUID,
        game: String,
        vector: FloatArray,
        text: String,
        createdOn: Instant
    ) {
        val sql = """
        INSERT INTO btm.embeddings (id, text_bit_id, game, vector, text, created_on)
        VALUES (:id, :textBitId, CAST(:game AS game_enum), CAST(:vector AS vector), :text, :createdOn);
        """

        val params = mapOf(
            "id" to id,
            "textBitId" to textBitId,
            "game" to game,
            "vector" to PGvector(vector),
            "text" to text,
            "createdOn" to Timestamp.from(createdOn)
        )

        jdbcTemplate.update(sql, params)
    }

    fun getNearestEmbeddings(target: FloatArray, game: String): List<Embedding> {
        val sql = """
            SELECT * FROM btm.embeddings 
            WHERE game = CAST(:game AS game_enum) 
            ORDER BY vector <-> :target LIMIT 5
        """.trimIndent()
        val params = MapSqlParameterSource(mapOf(
            "game" to game,
            "target" to PGvector(target)
        ))
        return jdbcTemplate.query(sql, params, EmbeddingRowMapper)
    }

    fun deleteByTextBitId(uuid: UUID) {
        val sql = """
            DELETE FROM btm.embeddings
            WHERE text_bit_id = :uuid;
        """
        jdbcTemplate.update(sql, mapOf("uuid" to uuid))
    }

}

class Embedding(
    val id: UUID,
    val game: String,
    val vector: FloatArray,
    val text: String,
    val createdOn: Instant
)

object EmbeddingRowMapper: RowMapper<Embedding> {
    override fun mapRow(rs: ResultSet, rowNum: Int) = Embedding(
        id = UUID.fromString(rs.getString("id")),
        game = rs.getString("game"),
        vector = (rs.getObject("vector") as PGvector?)?.toArray() ?: error("vector missing"),
        text = rs.getString("text"),
        createdOn = rs.getTimestamp("created_on").toInstant()
    )
}
