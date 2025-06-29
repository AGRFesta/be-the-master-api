package org.agrfesta.btm.api

import org.agrfesta.btm.api.providers.openai.OpenAiConfiguration
import org.agrfesta.btm.api.services.PromptEnhanceConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableConfigurationProperties(OpenAiConfiguration::class, PromptEnhanceConfiguration::class)
@EnableScheduling
class BtmApplication

fun main(args: Array<String>) {
    runApplication<BtmApplication>(*args)
}
