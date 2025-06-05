package org.agrfesta.btm.api.persistence.jdbc.repositories

import org.agrfesta.btm.api.model.EmbeddingStatus
import org.agrfesta.btm.api.persistence.jdbc.entities.TranslationEntity
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.util.*

@Repository
class TranslationsRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {

    fun insert(entity: TranslationEntity) {
        val sql = """
        INSERT INTO btm.translations (
            id,
            text_bit_id,
            language_code,
            text,
            embedding_status,
            created_on
        ) VALUES (
            :id,
            :chunkId,
            :languageCode,
            :text,
            CAST(:embeddingStatus AS embedding_status_enum),
            :createdOn
        )
    """.trimIndent()

        val params = mapOf(
            "id" to entity.id,
            "chunkId" to entity.chunkId,
            "languageCode" to entity.languageCode,
            "text" to entity.text,
            "embeddingStatus" to entity.embeddingStatus.name,
            "createdOn" to Timestamp.from(entity.createdOn)
        )

        jdbcTemplate.update(sql, params)
    }

    fun findTranslationByLanguage(chunkId: UUID, language: String): TranslationEntity? {
        val sql = """
            SELECT 
                id,
                text_bit_id,
                language_code,
                text,
                embedding_status,
                created_on
            FROM btm.translations
            WHERE text_bit_id = :chunkId
            and language_code = :language
        """.trimIndent()

        val params = mapOf("chunkId" to chunkId, "language" to language)

        val trs: TranslationEntity? = try {
            jdbcTemplate.queryForObject(sql, params) { rs, _ ->
                TranslationEntity(
                    id = UUID.fromString(rs.getString("id")),
                    chunkId = UUID.fromString(rs.getString("text_bit_id")),
                    languageCode = rs.getString("language_code"),
                    text = rs.getString("text"),
                    embeddingStatus = EmbeddingStatus.valueOf(rs.getString("embedding_status")),
                    createdOn = rs.getTimestamp("created_on").toInstant()
                )
            }
        } catch (e: EmptyResultDataAccessException) {
            null
        }
        return trs
    }

    fun findTranslations(chunkId: UUID): Collection<TranslationEntity> {
        val sql = """
            SELECT 
                id,
                text_bit_id,
                language_code,
                text,
                embedding_status,
                created_on
            FROM btm.translations
            WHERE text_bit_id = :chunkId
        """.trimIndent()

        val params = mapOf("chunkId" to chunkId)

        return jdbcTemplate.query(sql, params) { rs, _ ->
            TranslationEntity(
                id = UUID.fromString(rs.getString("id")),
                chunkId = UUID.fromString(rs.getString("text_bit_id")),
                languageCode = rs.getString("language_code"),
                text = rs.getString("text"),
                embeddingStatus = EmbeddingStatus.valueOf(rs.getString("embedding_status")),
                createdOn = rs.getTimestamp("created_on").toInstant()
            )
        }
    }

    fun update(
        id: UUID,
        text: String? = null,
        embeddingStatus: EmbeddingStatus? = null
    ) {
        val updates = mutableListOf<String>()
        val params = mutableMapOf<String, Any>("id" to id)

        if (text != null) {
            updates.add("text = :text")
            params["text"] = text
        }

        if (embeddingStatus != null) {
            updates.add("embedding_status = CAST(:embeddingStatus AS embedding_status_enum)")
            params["embeddingStatus"] = embeddingStatus.name
        }

        if (updates.isEmpty()) {
            return
        }

        val sql = """
            UPDATE btm.translations
            SET ${updates.joinToString(", ")}
            WHERE id = :id
        """.trimIndent()

        jdbcTemplate.update(sql, params)
    }

    fun delete(uuid: UUID) {
        val sql = """
            DELETE FROM btm.translations
            WHERE id = :uuid;
        """
        jdbcTemplate.update(sql, mapOf("uuid" to uuid))
    }

}
