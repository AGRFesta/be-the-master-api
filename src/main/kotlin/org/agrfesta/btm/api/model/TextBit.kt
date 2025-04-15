package org.agrfesta.btm.api.model

import java.util.UUID

data class TextBit(
    val id: UUID,
    val game: Game,
    val text: String
)
