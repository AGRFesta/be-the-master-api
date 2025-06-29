package org.agrfesta.btm.api.services

import org.agrfesta.btm.api.model.SupportedLanguage
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "prompts.enhance")
data class PromptEnhanceConfiguration(
    val basicTemplate: Map<SupportedLanguage, String>
)
