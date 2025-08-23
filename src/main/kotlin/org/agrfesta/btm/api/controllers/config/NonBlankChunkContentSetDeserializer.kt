package org.agrfesta.btm.api.controllers.config

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.node.ArrayNode
import org.agrfesta.btm.api.controllers.ChunkContent

/**
 * Custom deserializer for a Set of [ChunkContent] objects.
 *
 * This deserializer:
 * - Reads a JSON array of objects matching the [ChunkContent] structure.
 * - Filters out any element whose `text` property is blank or consists only of whitespace.
 * - Converts the remaining elements into a Set, removing duplicates based on equality.
 *
 * Example JSON input:
 * [
 *   { "text": "Valid content", "nonSemanticaPart": "meta" },
 *   { "text": "   " }, // This will be ignored because text is blank
 *   { "text": "Another chunk" }
 * ]
 */
class NonBlankChunkContentSetDeserializer : JsonDeserializer<Set<ChunkContent>>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Set<ChunkContent> {
        val node = p.codec.readTree<ArrayNode>(p)

        return node.mapNotNull { elementNode ->
            // Deserialize JSON node into a ChunkContent object
            val chunk = p.codec.treeToValue(elementNode, ChunkContent::class.java)

            // Keep only elements with non-blank text
            chunk?.takeIf { it.text.isNotBlank() }
        }.toSet()
    }
}