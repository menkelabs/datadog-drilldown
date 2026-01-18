package com.example.rca

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Spring Boot application for Embabel-Dice RCA integration.
 * 
 * This module provides:
 * - Datadog integration for fetching logs, metrics, and APM data
 * - AI-powered root cause analysis using Embabel agent framework
 * - Dice integration for data ingestion and chat-based incident advice
 */
@SpringBootApplication
class EmbabelDiceRcaApplication

fun main(args: Array<String>) {
    runApplication<EmbabelDiceRcaApplication>(*args)
}
