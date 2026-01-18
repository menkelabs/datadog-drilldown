package com.example.rca.dice

import com.example.rca.dice.model.*
import org.springframework.context.ApplicationEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.slf4j.LoggerFactory

/**
 * Spring application events for Dice ingestion.
 */
class AlertIngestedEvent(
    source: Any,
    val trigger: AlertTrigger,
    val incidentId: String
) : ApplicationEvent(source)

class LogsIngestedEvent(
    source: Any,
    val event: LogStreamEvent,
    val incidentId: String
) : ApplicationEvent(source)

class AnomalyIngestedEvent(
    source: Any,
    val anomaly: MetricAnomaly,
    val incidentId: String
) : ApplicationEvent(source)

class ManualReportIngestedEvent(
    source: Any,
    val report: ManualIncidentReport,
    val incidentId: String
) : ApplicationEvent(source)

/**
 * Event handler for Dice ingestion events.
 * Provides extension points for custom processing.
 */
@Component
class DiceEventHandler {
    private val logger = LoggerFactory.getLogger(javaClass)

    @EventListener
    fun handleAlertIngested(event: AlertIngestedEvent) {
        logger.debug(
            "Alert ingested: {} -> incident {}",
            event.trigger.id,
            event.incidentId
        )
    }

    @EventListener
    fun handleLogsIngested(event: LogsIngestedEvent) {
        logger.debug(
            "Logs ingested: {} entries -> incident {}",
            event.event.logs.size,
            event.incidentId
        )
    }

    @EventListener
    fun handleAnomalyIngested(event: AnomalyIngestedEvent) {
        logger.debug(
            "Anomaly ingested: {} deviation={}% -> incident {}",
            event.anomaly.metricName,
            event.anomaly.deviationPercent,
            event.incidentId
        )
    }

    @EventListener
    fun handleManualReportIngested(event: ManualReportIngestedEvent) {
        logger.debug(
            "Manual report ingested: {} -> incident {}",
            event.report.title,
            event.incidentId
        )
    }
}

/**
 * Interface for custom Dice event processors.
 */
interface DiceEventProcessor {
    fun onAlertIngested(trigger: AlertTrigger, incidentId: String) {}
    fun onLogsIngested(event: LogStreamEvent, incidentId: String) {}
    fun onAnomalyIngested(anomaly: MetricAnomaly, incidentId: String) {}
    fun onManualReportIngested(report: ManualIncidentReport, incidentId: String) {}
}
