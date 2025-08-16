package org.agrfesta.btm.api.model

import java.util.UUID

class Chunk(
    val id: UUID, //TODO reconsider this
    val game: Game,
    val topic: Topic,
    translations: Set<Translation> //TODO reconsider this, maybe have more sense as map
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
    val language: SupportedLanguage
)
