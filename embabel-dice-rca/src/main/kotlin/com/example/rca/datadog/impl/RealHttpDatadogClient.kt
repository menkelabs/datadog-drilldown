package com.example.rca.datadog.impl

import com.example.rca.datadog.DatadogClient
import com.example.rca.datadog.DatadogException
import com.example.rca.datadog.dto.*
import org.springframework.web.reactive.function.client.WebClient
import java.time.Instant

/**
 * Real implementation of DatadogClient using HTTP REST API.
 * This is the "Real" service that will be used in production.
 */
class RealHttpDatadogClient(
    private val apiKey: String,
    private val appKey: String,
    private val site: String
) : DatadogClient {

    private val v1BaseUrl = "https://api.$site/api/v1"
    private val v2BaseUrl = "https://api.$site/api/v2"

    private val webClient = WebClient.builder()
        .defaultHeader("DD-API-KEY", apiKey)
        .defaultHeader("DD-APPLICATION-KEY", appKey)
        .defaultHeader("Content-Type", "application/json")
        .build()

    override fun getMonitor(monitorId: Long): MonitorResponse {
        val response = webClient.get()
            .uri("$v1BaseUrl/monitor/$monitorId")
            .retrieve()
            .bodyToMono(Map::class.java)
            .block() ?: throw DatadogException("Empty response from monitor API")

        @Suppress("UNCHECKED_CAST")
        return (response as Map<String, Any>).toMonitorResponse()
    }

    override fun queryMetrics(query: String, start: Instant, end: Instant): MetricResponse {
        val response = webClient.get()
            .uri { builder ->
                builder.path("$v1BaseUrl/query")
                    .queryParam("from", start.epochSecond)
                    .queryParam("to", end.epochSecond)
                    .queryParam("query", query)
                    .build()
            }
            .retrieve()
            .bodyToMono(Map::class.java)
            .block() ?: throw DatadogException("Empty response from metrics API")

        @Suppress("UNCHECKED_CAST")
        return (response as Map<String, Any>).toMetricResponse()
    }

    override fun searchLogs(
        query: String,
        start: Instant,
        end: Instant,
        limit: Int,
        maxPages: Int
    ): List<LogEntry> {
        val logs = mutableListOf<LogEntry>()
        var cursor: String? = null

        repeat(maxPages) {
            val body = buildMap {
                put("filter", mapOf(
                    "from" to start.toString(),
                    "to" to end.toString(),
                    "query" to query
                ))
                put("sort", "timestamp")
                put("page", buildMap {
                    put("limit", minOf(limit, 1000))
                    cursor?.let { put("cursor", it) }
                })
            }

            val response = webClient.post()
                .uri("$v2BaseUrl/logs/events/search")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map::class.java)
                .block() ?: return logs

            @Suppress("UNCHECKED_CAST")
            val data = (response["data"] as? List<Map<String, Any>>) ?: return logs
            logs.addAll(data.map { it.toLogEntry() })

            val meta = response["meta"] as? Map<*, *>
            val page = meta?.get("page") as? Map<*, *>
            cursor = page?.get("after") as? String
            if (cursor == null) return logs
        }

        return logs
    }

    override fun searchSpans(
        query: String,
        start: Instant,
        end: Instant,
        limit: Int,
        maxPages: Int
    ): List<SpanEntry> {
        val spans = mutableListOf<SpanEntry>()
        var cursor: String? = null

        repeat(maxPages) {
            val body = buildMap {
                put("filter", mapOf(
                    "from" to start.toString(),
                    "to" to end.toString(),
                    "query" to query
                ))
                put("sort", "timestamp")
                put("page", buildMap {
                    put("limit", minOf(limit, 1000))
                    cursor?.let { put("cursor", it) }
                })
            }

            val response = webClient.post()
                .uri("$v2BaseUrl/apm/events/search")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map::class.java)
                .block() ?: return spans

            @Suppress("UNCHECKED_CAST")
            val data = (response["data"] as? List<Map<String, Any>>) ?: return spans
            spans.addAll(data.map { it.toSpanEntry() })

            val meta = response["meta"] as? Map<*, *>
            val page = meta?.get("page") as? Map<*, *>
            cursor = page?.get("after") as? String
            if (cursor == null) return spans
        }

        return spans
    }

    override fun searchEvents(start: Instant, end: Instant, tags: String?): EventResponse {
        val response = webClient.get()
            .uri { builder ->
                builder.path("$v1BaseUrl/events")
                    .queryParam("start", start.epochSecond)
                    .queryParam("end", end.epochSecond)
                tags?.let { builder.queryParam("tags", it) }
                builder.build()
            }
            .retrieve()
            .bodyToMono(Map::class.java)
            .block() ?: throw DatadogException("Empty response from events API")

        @Suppress("UNCHECKED_CAST")
        return (response as Map<String, Any>).toEventResponse()
    }
}
