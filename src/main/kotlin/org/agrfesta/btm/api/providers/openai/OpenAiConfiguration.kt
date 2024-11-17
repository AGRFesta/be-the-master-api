package org.agrfesta.btm.api.providers.openai

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "providers.openai")
data class OpenAiConfiguration(
    val baseUrl: String,
    val apiKey: String
)
