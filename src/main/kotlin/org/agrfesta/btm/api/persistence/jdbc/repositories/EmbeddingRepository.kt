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

/**
 * Repository for performing JDBC operations on the `btm.embeddings` table.
 *
 * Supports inserting, retrieving, and deleting embedding records,
 * as well as performing approximate nearest neighbor searches using PGVector.
 *
 * This implementation uses `NamedParameterJdbcTemplate` and is specific to PostgreSQL with `pgvector` support.
 */
@Repository
class EmbeddingRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {

    /**
     * Inserts a new embedding into the `btm.embeddings` table.
     *
     * @param id unique identifier for the embedding.
     * @param translationId foreign key referencing the associated translation.
     * @param vector the embedding vector to store.
     * @param createdOn timestamp of creation.
     */
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

    /**
     * Retrieves the embedding associated with a given translation ID.
     *
     * @param translationId ID of the translation.
     * @return [EmbeddingEntity] if found, or `null` if no match exists.
     */
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

    /**
     * Performs a nearest neighbor search using the `<=>` operator provided by pgvector.
     * Returns a list of texts and their distances to the target embedding, filtered by game, topic, and language.
     *
     * @param target the target embedding vector to search around.
     * @param game the game (as enum) to filter by.
     * @param topic the topic (as enum) to filter by.
     * @param language the language (as enum) to filter by.
     * @param limit maximum number of results to return.
     * @return list of (text, distance) pairs sorted by ascending distance.
     */
    fun getNearestEmbeddings(
        target: FloatArray,
        game: String,
        topic: String,
        language: String,
        limit: Int
    ): List<Pair<String, Double>> {
        val sql = """
            SELECT t.text, e.vector <=> :target AS distance
            FROM btm.embeddings e
            JOIN btm.translations t ON e.translation_id = t.id
            JOIN btm.chunks tb ON t.chunk_id = tb.id
            WHERE tb.game = CAST(:game AS game_enum)
              AND tb.topic = CAST(:topic AS topic_enum)
              AND t.language = CAST(:language AS supported_language_enum)
            ORDER BY distance ASC
            LIMIT :limit
        """.trimIndent()

        val params = MapSqlParameterSource(mapOf(
            "game" to game,
            "topic" to topic,
            "language" to language,
            "target" to PGvector(target),
            "limit" to limit
        ))

        return jdbcTemplate.query(sql, params) { rs, _ ->
            rs.getString("text") to rs.getDouble("distance")
        }
    }

    /**
     * Deletes the embedding associated with the given translation ID.
     *
     * @param uuid the ID of the translation to delete the embedding for.
     */
    fun deleteByTranslationId(uuid: UUID) {
        val sql = """
            DELETE FROM btm.embeddings
            WHERE translation_id = :uuid;
        """
        jdbcTemplate.update(sql, mapOf("uuid" to uuid))
    }

}

/**
 * Maps a row of the `btm.embeddings` table into an [EmbeddingEntity].
 *
 * Expects the `vector` column to be a `PGvector` object. Fails if missing.
 */
object EmbeddingRowMapper: RowMapper<EmbeddingEntity> {
    override fun mapRow(rs: ResultSet, rowNum: Int) = EmbeddingEntity(
        id = UUID.fromString(rs.getString("id")),
        translationId = UUID.fromString(rs.getString("translation_id")),
        vector = (rs.getObject("vector") as PGvector?)?.toArray() ?: error("vector missing"),
        createdOn = rs.getTimestamp("created_on").toInstant()
    )
}
