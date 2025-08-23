package org.agrfesta.btm.api.model

data class Translation(
    val text: String,
    val language: SupportedLanguage,
    val nonSemanticPart: String? = null
)
