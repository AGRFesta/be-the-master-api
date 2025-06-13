package org.agrfesta.btm.api.persistence

import com.pgvector.PGvector
import org.agrfesta.btm.api.model.Embedding
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import java.sql.ResultSet
import java.time.Instant
import java.util.*

@Service
class TestingChunksRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {

    fun findById(id: UUID): ChunkEntity? {
        val sql = """SELECT * FROM btm.chunks WHERE id = :id;"""
        val params = MapSqlParameterSource(mapOf("id" to id))
        return jdbcTemplate.query(sql, params, ChunkRowMapper)
            .firstOrNull()
    }

    fun getTranslationWithEmbedding(
        language: String,
        text: String
    ): TranslationWithEmbedding? {
        val sql = """
        SELECT 
            t.id AS translation_id,
            t.text,
            t.language_code,
            t.embedding_status,
            t.text_bit_id,
            e.id AS embedding_id,
            e.vector,
            e.created_on AS embedding_created_on
        FROM btm.translations t
        LEFT JOIN btm.embeddings e ON t.id = e.translation_id
        WHERE t.language_code = :language 
        AND t.text = :text;
    """.trimIndent()
        val params = MapSqlParameterSource(mapOf("language" to language, "text" to text))
        return jdbcTemplate.query(sql, params, TranslationWithEmbeddingRowMapper)
            .firstOrNull()
    }

}

object ChunkRowMapper: RowMapper<ChunkEntity> {
    override fun mapRow(rs: ResultSet, rowNum: Int) = ChunkEntity(
        id = UUID.fromString(rs.getString("id")),
        game = rs.getString("game"),
        topic = rs.getString("topic"),
        createdOn = rs.getTimestamp("created_on").toInstant(),
        updatedOn = rs.getTimestamp("updated_on")?.toInstant()
    )
}

object TranslationWithEmbeddingRowMapper: RowMapper<TranslationWithEmbedding> {

    override fun mapRow(rs: ResultSet, rowNum: Int): TranslationWithEmbedding {
        val pgVec = rs.getObject("vector") as? PGvector
        return TranslationWithEmbedding(
            translationId = UUID.fromString(rs.getString("translation_id")),
            chunkId = UUID.fromString(rs.getString("text_bit_id")),
            text = rs.getString("text"),
            languageCode = rs.getString("language_code"),
            embeddingStatus = rs.getString("embedding_status"),
            embeddingId = rs.getString("embedding_id")?.let(UUID::fromString),
            vector = pgVec?.toArray(),
            embeddingCreatedOn = rs.getTimestamp("embedding_created_on")?.toInstant()
        )
    }

}

class ChunkEntity(
    val id: UUID,
    val game: String,
    val topic: String,
    val createdOn: Instant,
    val updatedOn: Instant?
)

class TranslationWithEmbedding(
    val translationId: UUID,
    val chunkId: UUID,
    val text: String,
    val languageCode: String,
    val embeddingStatus: String,
    val embeddingId: UUID?,
    val vector: Embedding?,
    val embeddingCreatedOn: Instant?
)