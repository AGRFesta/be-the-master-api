package org.agrfesta.btm.api.persistence

import org.agrfesta.btm.api.model.Game
import org.agrfesta.btm.api.model.TextBit
import org.agrfesta.btm.api.model.Topic
import org.springframework.transaction.annotation.Transactional
import java.util.*

interface TextBitsDao {

    /**
     * Finds [TextBit] by [textBitId].
     *
     * @param textBitId [TextBit] unique identifier.
     * @return found [TextBit] otherwise null.
     */
    fun findTextBit(textBitId: UUID): TextBit?

    /**
     * Persists a [TextBit] by [topic] and [game].
     *
     * @param topic [TextBit] related [Topic].
     * @param game [TextBit] related [Game].
     * @return [UUID] assigned to persisted [TextBit].
     */
    @Transactional
    fun persist(topic: Topic, game: Game): UUID

    /**
     * Deletes a [TextBit] by [textBitId].
     *
     * @param textBitId [TextBit] unique identifier.
     */
    @Transactional
    fun delete(textBitId: UUID)

}
