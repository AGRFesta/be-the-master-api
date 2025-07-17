package org.agrfesta.btm.api.controllers

import org.agrfesta.btm.api.model.Embedding
import org.agrfesta.btm.api.model.EmbeddingStatus.EMBEDDED
import org.agrfesta.btm.api.model.Game
import org.agrfesta.btm.api.model.SupportedLanguage
import org.agrfesta.btm.api.model.Topic
import org.agrfesta.btm.api.persistence.jdbc.entities.aTranslationEntity
import org.agrfesta.btm.api.persistence.jdbc.repositories.ChunksRepository
import org.agrfesta.btm.api.persistence.jdbc.repositories.EmbeddingRepository
import org.agrfesta.btm.api.persistence.jdbc.repositories.TranslationsRepository
import org.agrfesta.btm.api.services.utils.toNoNanoSec
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.*

interface RagAsserter {

    fun givenChunkEmbedding(
        game: Game = aGame(),
        topic: Topic = aTopic(),
        language: SupportedLanguage = aLanguage(),
        text: String,
        embedding: Embedding
    ): UUID

}

@Service
class RagAsserterImpl(
    @Autowired private val chunksRepo: ChunksRepository,
    @Autowired private val embeddingRepo: EmbeddingRepository,
    @Autowired private val translationsRepo: TranslationsRepository
): RagAsserter {
    private val now = Instant.now().toNoNanoSec()

    override fun givenChunkEmbedding(
        game: Game,
        topic: Topic,
        language: SupportedLanguage,
        text: String,
        embedding: Embedding
    ): UUID {
        val chunk = aChunk(game = game, topic = topic)
        chunksRepo.insert(chunk.id, game, topic, createdOn = now)
        val translation = aTranslationEntity(
            chunkId = chunk.id,
            embeddingStatus = EMBEDDED,
            text = text,
            language = language)
        translationsRepo.insert(translation)
        embeddingRepo.insertEmbedding(UUID.randomUUID(), translation.id, embedding, now)
        return translation.id
    }

}
