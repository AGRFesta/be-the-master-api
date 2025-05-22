package org.agrfesta.btm.api.model

import java.util.UUID

class TextBit(
    val id: UUID,
    val game: Game,
    val topic: Topic,
    val original: Translation,
    translations: Set<Translation>
) {
    val translations: Set<Translation> = translations
        .filter { it.text.isNotBlank() }
        .toMutableSet().also { it.remove(original) }
        .toSet()

    init {
        val languages = (this.translations + original).map { it.language }.toSet()
        require(languages.size == (this.translations.size +1)) {
            "Multiple translations in the same language are not allowed"
        }
    }
}

data class Translation(
    val text: String,
    val language: String
)
