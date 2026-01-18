package com.example.rca.dice

import com.example.rca.agent.RcaAgent
import com.example.rca.dice.model.*
import com.example.rca.domain.*
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Dice integration service for data ingestion and incident management.
 * 
 * This service handles:
 * - Alert trigger ingestion from monitoring systems
 * - Log stream processing
 * - Metric anomaly notifications
 * - Manual incident reports
 */
@Service
class DiceIngestionService(
    private val eventPublisher: ApplicationEventPublisher,
    private val rcaAgent: RcaAgent
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    
    // Active incidents tracked by ID
    private val activeIncidents = ConcurrentHashMap<String, IncidentContext>()

    /**
     * Ingest an alert trigger and potentially start RCA analysis.
     */
    fun ingestAlert(trigger: AlertTrigger): IngestionResult {
        logger.info("Ingesting alert: ${trigger.id} type=${trigger.alertType}")

        return try {
            // Create incident context from alert
            val incidentId = generateIncidentId()
            val windows = createWindowsFromAlert(trigger)
            val scope = Scope(
                service = trigger.service,
                env = trigger.env,
                tags = trigger.tags
            )

            val context = IncidentContext(
                id = incidentId,
                windows = windows,
                scope = scope,
                metadata = mutableMapOf(
                    "alert_id" to trigger.id,
                    "alert_type" to trigger.alertType.name,
                    "severity" to trigger.severity.name,
                    "source" to trigger.source
                )
            )

            // Store and start analysis
            activeIncidents[incidentId] = context
            
            // Publish event for other listeners
            eventPublisher.publishEvent(AlertIngestedEvent(this, trigger, incidentId))

            // Trigger async analysis if severity warrants it
            val analysisTriggered = if (trigger.severity <= AlertSeverity.WARNING) {
                rcaAgent.startAnalysis(context)
                true
            } else {
                false
            }

            IngestionResult(
                eventId = trigger.id,
                success = true,
                incidentId = incidentId,
                message = "Alert ingested successfully",
                analysisTriggered = analysisTriggered
            )
        } catch (e: Exception) {
            logger.error("Failed to ingest alert: ${trigger.id}", e)
            IngestionResult(
                eventId = trigger.id,
                success = false,
                message = "Failed: ${e.message}"
            )
        }
    }

    /**
     * Ingest a batch of logs, potentially correlating to existing incidents.
     */
    fun ingestLogStream(event: LogStreamEvent): IngestionResult {
        logger.info("Ingesting log stream: ${event.id} with ${event.logs.size} logs")

        return try {
            // Find or create incident context
            val incidentId = findMatchingIncident(event) ?: generateIncidentId()
            val context = activeIncidents.getOrPut(incidentId) {
                createContextFromLogEvent(incidentId, event)
            }

            // Convert and add logs to context
            context.metadata["log_count"] = 
                (context.metadata["log_count"] as? Int ?: 0) + event.logs.size

            eventPublisher.publishEvent(LogsIngestedEvent(this, event, incidentId))

            IngestionResult(
                eventId = event.id,
                success = true,
                incidentId = incidentId,
                message = "Logs ingested: ${event.logs.size} entries"
            )
        } catch (e: Exception) {
            logger.error("Failed to ingest logs: ${event.id}", e)
            IngestionResult(
                eventId = event.id,
                success = false,
                message = "Failed: ${e.message}"
            )
        }
    }

    /**
     * Ingest a metric anomaly event.
     */
    fun ingestMetricAnomaly(anomaly: MetricAnomaly): IngestionResult {
        logger.info("Ingesting metric anomaly: ${anomaly.id} metric=${anomaly.metricName}")

        return try {
            val incidentId = findMatchingIncident(anomaly) ?: generateIncidentId()
            val context = activeIncidents.getOrPut(incidentId) {
                createContextFromAnomaly(incidentId, anomaly)
            }

            // Add symptom from anomaly
            val symptom = Symptom(
                type = inferSymptomType(anomaly.metricName),
                queryOrSignature = anomaly.query,
                baselineValue = anomaly.expectedValue,
                incidentValue = anomaly.actualValue,
                percentChange = anomaly.deviationPercent
            )
            context.addSymptom(symptom)

            eventPublisher.publishEvent(AnomalyIngestedEvent(this, anomaly, incidentId))

            val analysisTriggered = if (anomaly.isSignificant) {
                rcaAgent.startAnalysis(context)
                true
            } else {
                false
            }

            IngestionResult(
                eventId = anomaly.id,
                success = true,
                incidentId = incidentId,
                message = "Anomaly ingested: ${anomaly.deviationPercent}% deviation",
                analysisTriggered = analysisTriggered
            )
        } catch (e: Exception) {
            logger.error("Failed to ingest anomaly: ${anomaly.id}", e)
            IngestionResult(
                eventId = anomaly.id,
                success = false,
                message = "Failed: ${e.message}"
            )
        }
    }

    /**
     * Ingest a manual incident report from user.
     */
    fun ingestManualReport(report: ManualIncidentReport): IngestionResult {
        logger.info("Ingesting manual report: ${report.id} title=${report.title}")

        return try {
            val incidentId = generateIncidentId()
            val now = Instant.now()
            val start = report.startTime ?: now.minus(Duration.ofMinutes(30))
            val end = report.endTime ?: now

            val windows = Windows.fromRange(start, end)
            val scope = Scope(
                service = report.service,
                env = report.env,
                tags = report.tags
            )

            val context = IncidentContext(
                id = incidentId,
                windows = windows,
                scope = scope,
                metadata = mutableMapOf(
                    "title" to report.title,
                    "description" to report.description,
                    "severity" to report.severity.name,
                    "source" to "manual"
                )
            )

            activeIncidents[incidentId] = context
            eventPublisher.publishEvent(ManualReportIngestedEvent(this, report, incidentId))

            // Always trigger analysis for manual reports
            rcaAgent.startAnalysis(context)

            IngestionResult(
                eventId = report.id,
                success = true,
                incidentId = incidentId,
                message = "Manual report ingested",
                analysisTriggered = true
            )
        } catch (e: Exception) {
            logger.error("Failed to ingest manual report: ${report.id}", e)
            IngestionResult(
                eventId = report.id,
                success = false,
                message = "Failed: ${e.message}"
            )
        }
    }

    /**
     * Get an active incident context by ID.
     */
    fun getIncident(incidentId: String): IncidentContext? = activeIncidents[incidentId]

    /**
     * List all active incidents.
     */
    fun listActiveIncidents(): List<IncidentContext> = activeIncidents.values.toList()

    /**
     * Close/archive an incident.
     */
    fun closeIncident(incidentId: String): Boolean {
        val removed = activeIncidents.remove(incidentId)
        return removed != null
    }

    // Private helpers

    private fun generateIncidentId(): String = "INC-${UUID.randomUUID().toString().take(8).uppercase()}"

    private fun createWindowsFromAlert(trigger: AlertTrigger): Windows {
        val anchor = trigger.timestamp
        return Windows.endingAt(anchor, windowMinutes = 30, baselineMinutes = 30)
    }

    private fun createContextFromLogEvent(incidentId: String, event: LogStreamEvent): IncidentContext {
        val windows = Windows.endingAt(event.timestamp, windowMinutes = 30, baselineMinutes = 30)
        return IncidentContext(
            id = incidentId,
            windows = windows,
            scope = Scope(service = event.service, env = event.env),
            metadata = mutableMapOf("source" to "log_stream")
        )
    }

    private fun createContextFromAnomaly(incidentId: String, anomaly: MetricAnomaly): IncidentContext {
        val windows = Windows.endingAt(anomaly.timestamp, windowMinutes = 30, baselineMinutes = 30)
        return IncidentContext(
            id = incidentId,
            windows = windows,
            scope = Scope(service = anomaly.service, env = anomaly.env),
            metadata = mutableMapOf("source" to "anomaly_detection")
        )
    }

    private fun findMatchingIncident(event: IngestionEvent): String? {
        // Try to correlate with existing incidents by service/env and time proximity
        val service = when (event) {
            is LogStreamEvent -> event.service
            is MetricAnomaly -> event.service
            else -> null
        }
        val env = when (event) {
            is LogStreamEvent -> event.env
            is MetricAnomaly -> event.env
            else -> null
        }

        if (service == null) return null

        return activeIncidents.entries.firstOrNull { (_, context) ->
            context.scope.service == service &&
            context.scope.env == env &&
            context.windows.incident.contains(event.timestamp)
        }?.key
    }

    private fun inferSymptomType(metricName: String): SymptomType {
        val name = metricName.lowercase()
        return when {
            "latency" in name || "duration" in name || "p95" in name -> SymptomType.LATENCY
            "error" in name -> SymptomType.ERROR_RATE
            "memory" in name || "heap" in name -> SymptomType.MEMORY
            "cpu" in name -> SymptomType.CPU
            else -> SymptomType.METRIC
        }
    }
}
