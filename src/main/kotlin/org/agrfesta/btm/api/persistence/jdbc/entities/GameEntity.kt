package org.agrfesta.btm.api.persistence.jdbc.entities

import java.util.*
import org.agrfesta.btm.api.model.Game

class GameEntity(
    val id: UUID,
    val name: String,
    val description: String? = null
) {
    fun toGame() = Game(
        name = name,
        description = description
    )
}
