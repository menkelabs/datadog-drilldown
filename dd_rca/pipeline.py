from __future__ import annotations

from dataclasses import asdict
from datetime import UTC, datetime
from typing import Any, Dict, List, Optional

from .analysis.apm import SpanView, group_dependency_stats, group_endpoint_stats, normalize_span
from .analysis.events import parse_events
from .analysis.logs import cluster_logs, merge_baseline_counts, rank_clusters
from .analysis.metrics import percent_change, summarize_metrics_query
from .analysis.scoring import score_log_clusters
from .datadog_client import DatadogClient, DatadogError
from .models import Candidate, Report, Symptom
from .scope import Scope, scope_from_logs, scope_from_monitor
from datetime import timedelta

from .windows import TimeWindow, Windows, _parse_ts, windows_ending_at


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

    clusters = merge_baseline_counts(incident_clusters=cluster_logs(incident_logs), baseline_logs=baseline_logs)
    top_clusters = rank_clusters(clusters, limit=10)
    candidates: List[Candidate] = score_log_clusters(top_clusters, limit=10)

    apm_findings, apm_candidates = _maybe_apm_attribution(client=client, scope=scope, windows=windows)
    candidates.extend(apm_candidates)
    candidates.sort(key=lambda c: c.score, reverse=True)

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
        "apm": apm_findings,
        "events": [asdict(e) for e in events],
        "candidates": [asdict(c) for c in candidates[:20]],
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

    clusters = merge_baseline_counts(incident_clusters=cluster_logs(incident_logs), baseline_logs=baseline_logs)
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

    apm_findings, apm_candidates = _maybe_apm_attribution(client=client, scope=scope, windows=windows)
    candidates.extend(apm_candidates)
    candidates.sort(key=lambda c: c.score, reverse=True)

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
        "apm": apm_findings,
        "events": [asdict(e) for e in events],
        "candidates": [asdict(c) for c in candidates[:20]],
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


def analyze_from_service(
    *,
    client: DatadogClient,
    service: str,
    env: str,
    start: str,
    end: str,
    mode: str = "latency",
) -> Report:
    inc = TimeWindow(start=_parse_ts(start), end=_parse_ts(end))
    if inc.end <= inc.start:
        raise ValueError("end must be after start")
    duration_s = int((inc.end - inc.start).total_seconds())
    baseline = TimeWindow(start=inc.start - timedelta(seconds=duration_s), end=inc.start)
    windows = Windows(incident=inc, baseline=baseline, anchor=inc.end)

    scope = Scope(service=service, env=env)

    symptom = _service_symptom(client=client, scope=scope, windows=windows, mode=mode)

    log_query = _default_log_query(scope) if mode != "errors" else _default_error_log_query(scope)
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
    clusters = merge_baseline_counts(incident_clusters=cluster_logs(incident_logs), baseline_logs=baseline_logs)
    top_clusters = rank_clusters(clusters, limit=15)
    candidates: List[Candidate] = score_log_clusters(top_clusters, limit=10)

    apm_findings, apm_candidates = _maybe_apm_attribution(client=client, scope=scope, windows=windows, mode=mode)
    candidates.extend(apm_candidates)
    candidates.sort(key=lambda c: c.score, reverse=True)

    events_resp = client.search_events(
        start=windows.incident.start_epoch,
        end=windows.incident.end_epoch,
        tags=scope.to_event_tag_query(),
    )
    events = parse_events(events_resp, limit=20)

    findings = {
        "service": {"service": service, "env": env, "mode": mode},
        "metric_symptom": asdict(symptom),
        "log_query_used": log_query,
        "incident_log_count": len(incident_logs),
        "baseline_log_count": len(baseline_logs),
        "log_clusters": [asdict(c) for c in top_clusters],
        "apm": apm_findings,
        "events": [asdict(e) for e in events],
        "candidates": [asdict(c) for c in candidates[:20]],
    }

    recs = _recommendations(symptom, top_clusters, events)

    return Report(
        meta={
            "seed_type": "service",
            "generated_at": _now_iso(),
            "dd_site": client.cfg.site,
            "input": {"service": service, "env": env, "start": start, "end": end, "mode": mode},
        },
        windows=windows.to_dict(),
        scope=scope.to_dict(),
        symptoms=[symptom],
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


def _default_error_log_query(scope: Scope) -> str:
    # for explicit errors mode, avoid relying on status_code only
    parts = []
    if scope.service:
        parts.append(f"service:{scope.service}")
    if scope.env:
        parts.append(f"env:{scope.env}")
    parts.append("(error OR @error.message:* OR @error.stack:* OR exception OR level:error OR @status:error)")
    return " ".join(parts).strip()


def _maybe_apm_attribution(
    *,
    client: DatadogClient,
    scope: Scope,
    windows: Windows,
    mode: str = "latency",
) -> tuple[Dict[str, Any], List[Candidate]]:
    if not scope.service or not scope.env:
        return {"enabled": False, "reason": "missing service/env"}, []

    # Query spans for the service/env. Keep it broad; filter in code.
    q = f"service:{scope.service} env:{scope.env}"
    try:
        inc_raw = client.search_spans(query=q, start_iso=windows.incident.start.isoformat(), end_iso=windows.incident.end.isoformat(), limit=1000, max_pages=2)
        base_raw = client.search_spans(query=q, start_iso=windows.baseline.start.isoformat(), end_iso=windows.baseline.end.isoformat(), limit=1000, max_pages=2)
    except DatadogError as e:
        return {"enabled": True, "error": str(e)}, []

    inc_spans = [normalize_span(x) for x in inc_raw if isinstance(x, dict)]
    base_spans = [normalize_span(x) for x in base_raw if isinstance(x, dict)]

    # Separate server vs client spans when possible.
    inc_server = [s for s in inc_spans if (s.span_kind or "").lower() == "server"]
    base_server = [s for s in base_spans if (s.span_kind or "").lower() == "server"]
    inc_client = [s for s in inc_spans if (s.span_kind or "").lower() == "client"]
    base_client = [s for s in base_spans if (s.span_kind or "").lower() == "client"]

    endpoints_inc = group_endpoint_stats(inc_server or inc_spans)
    endpoints_base = group_endpoint_stats(base_server or base_spans)
    deps_inc = group_dependency_stats(inc_client)
    deps_base = group_dependency_stats(base_client)

    candidates: List[Candidate] = []

    # Endpoint candidates: largest p95 regression (mode=latency) or error rate increase (mode=errors)
    endpoint_rows = []
    for res, inc in endpoints_inc.items():
        base = endpoints_base.get(res) or {}
        if mode == "errors":
            inc_er = inc.get("error_rate") or 0.0
            base_er = base.get("error_rate") or 0.0
            delta = inc_er - base_er
            endpoint_rows.append((delta, res, inc, base))
        else:
            inc_p95 = inc.get("p95_ms") or 0.0
            base_p95 = base.get("p95_ms") or 0.0
            delta = inc_p95 - base_p95
            endpoint_rows.append((delta, res, inc, base))
    endpoint_rows.sort(key=lambda t: t[0], reverse=True)
    for delta, res, inc, base in endpoint_rows[:5]:
        if delta <= 0:
            continue
        score = min(0.95, max(0.0, delta / (500.0 if mode != "errors" else 0.5)))
        candidates.append(
            Candidate(
                kind="endpoint",
                title=f"Endpoint regression: {res}",
                score=float(score),
                evidence={"incident": inc, "baseline": base, "delta": delta},
            )
        )

    # Dependency candidates: increased total duration or error rate
    dep_rows = []
    for dep, inc in deps_inc.items():
        base = deps_base.get(dep) or {}
        inc_dur = inc.get("total_duration_ms") or 0.0
        base_dur = base.get("total_duration_ms") or 0.0
        inc_er = inc.get("error_rate") or 0.0
        base_er = base.get("error_rate") or 0.0
        dep_rows.append(((inc_dur - base_dur), (inc_er - base_er), dep, inc, base))
    dep_rows.sort(key=lambda t: (t[0], t[1]), reverse=True)
    for dur_delta, err_delta, dep, inc, base in dep_rows[:7]:
        if dur_delta <= 0 and err_delta <= 0:
            continue
        score = min(0.95, max(0.0, (dur_delta / 2000.0) + (err_delta / 0.5)))
        candidates.append(
            Candidate(
                kind="dependency",
                title=f"Downstream suspect: {dep}",
                score=float(min(0.99, score)),
                evidence={"incident": inc, "baseline": base, "duration_delta_ms": dur_delta, "error_rate_delta": err_delta},
            )
        )

    candidates.sort(key=lambda c: c.score, reverse=True)
    findings = {
        "enabled": True,
        "query": q,
        "counts": {
            "incident_spans": len(inc_spans),
            "baseline_spans": len(base_spans),
            "incident_server_spans": len(inc_server),
            "baseline_server_spans": len(base_server),
            "incident_client_spans": len(inc_client),
            "baseline_client_spans": len(base_client),
        },
        "top_endpoints": [
            {"resource": r, "incident": endpoints_inc.get(r), "baseline": endpoints_base.get(r)}
            for _, r, _, _ in endpoint_rows[:10]
        ],
        "top_dependencies": [
            {"dependency": dep, "incident": deps_inc.get(dep), "baseline": deps_base.get(dep)}
            for _, _, dep, _, _ in dep_rows[:10]
        ],
    }
    return findings, candidates


def _service_symptom(*, client: DatadogClient, scope: Scope, windows: Windows, mode: str) -> Symptom:
    # Try several common APM metric name patterns and take the first with data.
    service = scope.service or ""
    env = scope.env or ""
    tagset = ",".join([t for t in [f"service:{service}" if service else "", f"env:{env}" if env else ""] if t])
    tag_expr = f"{{{tagset}}}" if tagset else "{}"

    if mode == "errors":
        candidates = [
            (f"errors_rate: sum:trace.{service}.request.errors{tag_expr}.as_count() / sum:trace.{service}.request.hits{tag_expr}.as_count()", "error_rate"),
            (f"errors: sum:trace.{service}.request.errors{tag_expr}.as_count()", "error_rate"),
        ]
    else:
        candidates = [
            (f"p95:trace.{service}.request.duration{tag_expr}", "latency"),
            (f"p95:trace.http.request.duration{tag_expr}", "latency"),
        ]

    chosen_query = None
    base_sum = None
    inc_sum = None
    for q, stype in candidates:
        try:
            b = client.query_metrics(q, start=windows.baseline.start_epoch, end=windows.baseline.end_epoch)
            i = client.query_metrics(q, start=windows.incident.start_epoch, end=windows.incident.end_epoch)
        except Exception:
            continue
        bsum = summarize_metrics_query(b)
        isum = summarize_metrics_query(i)
        if bsum.point_count > 0 or isum.point_count > 0:
            chosen_query = q
            base_sum = bsum
            inc_sum = isum
            symptom_type = stype
            break

    if not chosen_query or base_sum is None or inc_sum is None:
        # fall back to a "synthetic" symptom
        return Symptom(type="metric", query_or_signature="(no matching service metric found)", baseline_value=None, incident_value=None)

    return Symptom(
        type=symptom_type,
        query_or_signature=chosen_query,
        baseline_value=base_sum.value,
        incident_value=inc_sum.value,
        percent_change=percent_change(base_sum.value, inc_sum.value),
        peak_ts=_epoch_to_iso(inc_sum.peak_ts),
        peak_value=inc_sum.peak_value,
    )


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

