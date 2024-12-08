package org.agrfesta.btm.api.model

import com.fasterxml.jackson.databind.JsonNode

data class Party(
    val name: String,
    val game: Game,
    val members: Collection<JsonNode>
)
