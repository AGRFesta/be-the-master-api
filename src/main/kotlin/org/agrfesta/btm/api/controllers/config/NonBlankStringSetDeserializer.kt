package org.agrfesta.btm.api.controllers.config

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer

class NonBlankStringSetDeserializer : JsonDeserializer<Set<String>>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Set<String> {
        val node = p.codec.readTree<com.fasterxml.jackson.databind.node.ArrayNode>(p)
        return node.mapNotNull { it.asText()?.takeIf { text -> text.isNotBlank() } }.toSet()
    }
}
