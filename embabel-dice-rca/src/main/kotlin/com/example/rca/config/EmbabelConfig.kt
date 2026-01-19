package com.example.rca.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate

/**
 * Configuration for Embabel AI agent framework.
 */
@Configuration
class EmbabelConfig {

    @Value("\${embabel.model:gpt-4}")
    private lateinit var model: String

    @Value("\${embabel.temperature:0.3}")
    private var temperature: Double = 0.3

    @Bean
    fun restTemplate(): RestTemplate {
        return RestTemplate()
    }

    /**
     * Embabel client configuration.
     * In a real implementation, this would configure the Embabel SDK.
     */
    @Bean
    fun embabelClientConfig(): EmbabelClientConfig {
        return EmbabelClientConfig(
            model = model,
            temperature = temperature
        )
    }
}

/**
 * Configuration holder for Embabel client.
 */
data class EmbabelClientConfig(
    val model: String,
    val temperature: Double,
    val maxTokens: Int = 4096,
    val systemPromptFile: String = "prompts/rca-system-prompt.txt"
)
