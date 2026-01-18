package com.example.rca

import com.example.rca.agent.RcaAgentProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

/**
 * Spring Boot application for Embabel-Dice RCA Agent.
 *
 * This module provides AI-powered incident investigation using:
 * - Embabel Agent framework for autonomous reasoning
 * - Datadog integration for fetching logs, metrics, and APM data
 * - Dice integration for data ingestion and chat-based incident advice
 *
 * Run with: mvn spring-boot:run
 *
 * The agent shell provides an interactive CLI for investigating incidents.
 * Type your incident description and the agent will:
 * 1. Categorize the incident type
 * 2. Collect evidence from Datadog
 * 3. Analyze root causes
 * 4. Generate actionable recommendations
 */
@SpringBootApplication
@EnableConfigurationProperties(RcaAgentProperties::class)
class EmbabelDiceRcaApplication

fun main(args: Array<String>) {
    runApplication<EmbabelDiceRcaApplication>(*args)
}
