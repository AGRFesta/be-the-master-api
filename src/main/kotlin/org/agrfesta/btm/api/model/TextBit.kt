package org.agrfesta.btm.api.model

import java.util.UUID

class TextBit(
    val id: UUID,
    val game: Game,
    val topic: Topic,
    translations: Set<Translation>
) {
    val translations: Set<Translation> = translations
        .filter { it.text.isNotBlank() }
        .toSet()

    init {
        val languages = this.translations.map { it.language }.toSet()
        require(languages.size == (this.translations.size)) {
            "Multiple translations in the same language are not allowed"
        }
    }
}

data class Translation(
    val text: String,
    val language: String
)
