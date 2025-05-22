package org.agrfesta.btm.api.persistence.jdbc.repositories

import com.pgvector.PGvector
import org.agrfesta.btm.api.persistence.jdbc.entities.EmbeddingEntity
import org.springframework.dao.EmptyResultDataAccessException
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
        translationId: UUID,
        vector: FloatArray,
        createdOn: Instant
    ) {
        val sql = """
        INSERT INTO btm.embeddings (id, translation_id, vector, created_on)
        VALUES (:id, :translationId, CAST(:vector AS vector), :createdOn);
        """

        val params = mapOf(
            "id" to id,
            "translationId" to translationId,
            "vector" to PGvector(vector),
            "createdOn" to Timestamp.from(createdOn)
        )

        jdbcTemplate.update(sql, params)
    }

    fun findEmbeddingByTranslationId(translationId: UUID): EmbeddingEntity? {
        val sql = """SELECT * FROM btm.embeddings WHERE translation_id = :translationId"""
        val params = mapOf("translationId" to translationId)
        val embedding: EmbeddingEntity? = try {
            jdbcTemplate.queryForObject(sql, params, EmbeddingRowMapper)
        } catch (e: EmptyResultDataAccessException) {
            null
        }
        return embedding
    }

    fun getNearestEmbeddings(target: FloatArray, game: String): List<EmbeddingEntity> {
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

    fun deleteByTranslationId(uuid: UUID) {
        val sql = """
            DELETE FROM btm.embeddings
            WHERE translation_id = :uuid;
        """
        jdbcTemplate.update(sql, mapOf("uuid" to uuid))
    }

}

object EmbeddingRowMapper: RowMapper<EmbeddingEntity> {
    override fun mapRow(rs: ResultSet, rowNum: Int) = EmbeddingEntity(
        id = UUID.fromString(rs.getString("id")),
        translationId = UUID.fromString(rs.getString("translation_id")),
        vector = (rs.getObject("vector") as PGvector?)?.toArray() ?: error("vector missing"),
        createdOn = rs.getTimestamp("created_on").toInstant()
    )
}
