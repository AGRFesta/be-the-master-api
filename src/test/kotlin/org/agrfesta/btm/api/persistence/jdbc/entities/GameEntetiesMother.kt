package org.agrfesta.btm.api.persistence.jdbc.entities

import java.util.UUID
import org.agrfesta.test.mothers.aRandomUniqueString

fun aGameEntity(
    id: UUID = UUID.randomUUID(),
    name: String = aRandomUniqueString(),
    description: String? = null
) = GameEntity(id, name, description)
