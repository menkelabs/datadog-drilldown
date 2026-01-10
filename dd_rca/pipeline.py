from __future__ import annotations

from dataclasses import asdict
from datetime import UTC, datetime
from typing import Any, Dict, List, Optional

from .analysis.events import parse_events
from .analysis.logs import merge_baseline_counts, rank_clusters
from .analysis.metrics import percent_change, summarize_metrics_query
from .analysis.scoring import score_log_clusters
from .datadog_client import DatadogClient
from .models import Candidate, Report, Symptom
from .scope import Scope, scope_from_logs, scope_from_monitor
from .windows import Windows, windows_ending_at


def _now_iso() -> str:
    return datetime.now(tz=UTC).isoformat()


def analyze_from_monitor(
    *,
    client: DatadogClient,
    monitor_id: int,
    trigger_ts: Optional[str],
    window_minutes: int,
    baseline_minutes: int,
) -> Report:
    windows = windows_ending_at(anchor_ts=trigger_ts, window_minutes=window_minutes, baseline_minutes=baseline_minutes)
    monitor = client.get_monitor(monitor_id)
    scope = scope_from_monitor(monitor)
    query = str(monitor.get("query") or "").strip()

    # Symptom: treat monitor query as the primary metric signal
    baseline_resp = client.query_metrics(query, start=windows.baseline.start_epoch, end=windows.baseline.end_epoch)
    incident_resp = client.query_metrics(query, start=windows.incident.start_epoch, end=windows.incident.end_epoch)
    base_sum = summarize_metrics_query(baseline_resp)
    inc_sum = summarize_metrics_query(incident_resp)

    sym = Symptom(
        type=_symptom_type_from_query(query),
        query_or_signature=query,
        baseline_value=base_sum.value,
        incident_value=inc_sum.value,
        percent_change=percent_change(base_sum.value, inc_sum.value),
        peak_ts=_epoch_to_iso(inc_sum.peak_ts),
        peak_value=inc_sum.peak_value,
    )

    # Logs: if we can infer service/env, pull error-ish logs for context
    log_query = _default_log_query(scope)
    incident_logs = client.search_logs(
        query=log_query,
        start_iso=windows.incident.start.isoformat(),
        end_iso=windows.incident.end.isoformat(),
    )
    baseline_logs = client.search_logs(
        query=log_query,
        start_iso=windows.baseline.start.isoformat(),
        end_iso=windows.baseline.end.isoformat(),
    )

    clusters = merge_baseline_counts(
        incident_clusters=__import__("dd_rca.analysis.logs", fromlist=["cluster_logs"]).cluster_logs(incident_logs),
        baseline_logs=baseline_logs,
    )
    top_clusters = rank_clusters(clusters, limit=10)
    candidates: List[Candidate] = score_log_clusters(top_clusters, limit=10)

    # Events
    events_resp = client.search_events(
        start=windows.incident.start_epoch,
        end=windows.incident.end_epoch,
        tags=scope.to_event_tag_query(),
    )
    events = parse_events(events_resp, limit=20)

    findings = {
        "monitor": {
            "id": monitor.get("id"),
            "name": monitor.get("name"),
            "type": monitor.get("type"),
            "query": query,
            "tags": monitor.get("tags"),
        },
        "log_query_used": log_query,
        "log_clusters": [asdict(c) for c in top_clusters],
        "events": [asdict(e) for e in events],
        "candidates": [asdict(c) for c in candidates],
    }

    recs = _recommendations(sym, top_clusters, events)

    return Report(
        meta={
            "seed_type": "monitor",
            "generated_at": _now_iso(),
            "dd_site": client.cfg.site,
            "input": {
                "monitor_id": int(monitor_id),
                "trigger_ts": trigger_ts,
                "window_minutes": int(window_minutes),
                "baseline_minutes": int(baseline_minutes),
            },
        },
        windows=windows.to_dict(),
        scope=scope.to_dict(),
        symptoms=[sym],
        findings=findings,
        recommendations=recs,
    )


def analyze_from_logs(
    *,
    client: DatadogClient,
    log_query: str,
    anchor_ts: Optional[str],
    window_minutes: int,
    baseline_minutes: int,
) -> Report:
    windows = windows_ending_at(anchor_ts=anchor_ts, window_minutes=window_minutes, baseline_minutes=baseline_minutes)

    incident_logs = client.search_logs(
        query=log_query,
        start_iso=windows.incident.start.isoformat(),
        end_iso=windows.incident.end.isoformat(),
    )
    baseline_logs = client.search_logs(
        query=log_query,
        start_iso=windows.baseline.start.isoformat(),
        end_iso=windows.baseline.end.isoformat(),
    )

    scope = scope_from_logs(incident_logs) if incident_logs else Scope()

    clusters = merge_baseline_counts(
        incident_clusters=__import__("dd_rca.analysis.logs", fromlist=["cluster_logs"]).cluster_logs(incident_logs),
        baseline_logs=baseline_logs,
    )
    top_clusters = rank_clusters(clusters, limit=15)

    # Symptom: log volume delta + top signature
    base_count = len(baseline_logs)
    inc_count = len(incident_logs)
    sym = Symptom(
        type="log_signature",
        query_or_signature=log_query,
        baseline_value=float(base_count),
        incident_value=float(inc_count),
        percent_change=percent_change(float(base_count) if base_count else None, float(inc_count)),
        peak_ts=None,
        peak_value=None,
    )

    candidates: List[Candidate] = score_log_clusters(top_clusters, limit=10)

    events_resp = client.search_events(
        start=windows.incident.start_epoch,
        end=windows.incident.end_epoch,
        tags=scope.to_event_tag_query(),
    )
    events = parse_events(events_resp, limit=20)

    findings = {
        "log_query": log_query,
        "incident_log_count": inc_count,
        "baseline_log_count": base_count,
        "log_clusters": [asdict(c) for c in top_clusters],
        "events": [asdict(e) for e in events],
        "candidates": [asdict(c) for c in candidates],
    }

    recs = _recommendations(sym, top_clusters, events)

    return Report(
        meta={
            "seed_type": "logs",
            "generated_at": _now_iso(),
            "dd_site": client.cfg.site,
            "input": {
                "log_query": log_query,
                "anchor_ts": anchor_ts,
                "window_minutes": int(window_minutes),
                "baseline_minutes": int(baseline_minutes),
            },
        },
        windows=windows.to_dict(),
        scope=scope.to_dict(),
        symptoms=[sym],
        findings=findings,
        recommendations=recs,
    )


def _epoch_to_iso(ts: Optional[int]) -> Optional[str]:
    if ts is None:
        return None
    try:
        return datetime.fromtimestamp(int(ts), tz=UTC).isoformat()
    except Exception:
        return None


def _symptom_type_from_query(query: str) -> str:
    q = (query or "").lower()
    if any(k in q for k in ("p95", "p99", "latency", "duration")):
        return "latency"
    if any(k in q for k in ("error", "5xx", "exceptions")):
        return "error_rate"
    return "metric"


def _default_log_query(scope: Scope) -> str:
    parts = []
    if scope.service:
        parts.append(f"service:{scope.service}")
    if scope.env:
        parts.append(f"env:{scope.env}")
    # best-effort "error-ish"
    parts.append("(@status:error OR status:error OR level:error OR @level:error OR @http.status_code:[500 TO 599])")
    return " ".join(parts).strip()


def _recommendations(sym: Symptom, top_clusters: List[Any], events: List[Any]) -> List[str]:
    recs: List[str] = []
    if sym.percent_change is not None and sym.percent_change > 20:
        recs.append("Confirm the regression start time using the incident window and the symptom peak timestamp.")
    if top_clusters:
        recs.append("Inspect the top log signature(s) and trace correlation (if available) to identify the failing component.")
    if events:
        recs.append("Review deploy/config/autoscaling events near the incident start for temporal alignment.")
    recs.append("If APM is enabled, pivot to the slowest endpoints and downstream services during the incident window.")
    return recs

