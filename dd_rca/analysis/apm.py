from __future__ import annotations

import math
from dataclasses import asdict, dataclass
from typing import Any, Dict, Iterable, List, Optional, Tuple


def _safe_str(x: Any) -> Optional[str]:
    if x is None:
        return None
    if isinstance(x, str):
        return x
    try:
        return str(x)
    except Exception:
        return None


def _safe_int(x: Any) -> Optional[int]:
    try:
        if x is None:
            return None
        return int(x)
    except Exception:
        return None


def _safe_float(x: Any) -> Optional[float]:
    try:
        if x is None:
            return None
        return float(x)
    except Exception:
        return None


def percentile(values: List[float], p: float) -> Optional[float]:
    if not values:
        return None
    if p <= 0:
        return min(values)
    if p >= 100:
        return max(values)
    xs = sorted(values)
    k = (len(xs) - 1) * (p / 100.0)
    f = math.floor(k)
    c = math.ceil(k)
    if f == c:
        return xs[int(k)]
    d0 = xs[int(f)] * (c - k)
    d1 = xs[int(c)] * (k - f)
    return d0 + d1


@dataclass(frozen=True)
class SpanView:
    timestamp: Optional[str]
    service: Optional[str]
    resource: Optional[str]
    name: Optional[str]
    span_kind: Optional[str]
    span_type: Optional[str]
    duration_ms: Optional[float]
    error: Optional[bool]
    http_status: Optional[int]
    trace_id: Optional[str]
    span_id: Optional[str]
    peer_service: Optional[str]


def _get_attr(attrs: Dict[str, Any], *keys: str) -> Any:
    for k in keys:
        if k in attrs:
            return attrs.get(k)
    return None


def normalize_span(item: Dict[str, Any]) -> SpanView:
    attrs = item.get("attributes") or {}
    # duration is commonly in nanoseconds; sometimes in ms. We'll infer by magnitude.
    raw_dur = _get_attr(attrs, "duration", "duration_ns", "durationNano")
    dur_f = _safe_float(raw_dur)
    dur_ms = None
    if dur_f is not None:
        dur_ms = dur_f / 1_000_000.0 if dur_f > 10_000 else dur_f

    raw_err = _get_attr(attrs, "error", "is_error")
    err = None
    if isinstance(raw_err, bool):
        err = raw_err
    elif isinstance(raw_err, (int, float)):
        err = bool(int(raw_err))

    http_status = _safe_int(_get_attr(attrs, "http.status_code", "http.status_code", "http.status"))

    span_kind = _safe_str(_get_attr(attrs, "span.kind", "span_kind", "span.kind"))
    span_type = _safe_str(_get_attr(attrs, "span.type", "span_type", "type"))

    return SpanView(
        timestamp=_safe_str(_get_attr(attrs, "timestamp")),
        service=_safe_str(_get_attr(attrs, "service")),
        resource=_safe_str(_get_attr(attrs, "resource", "resource_name")),
        name=_safe_str(_get_attr(attrs, "name")),
        span_kind=span_kind,
        span_type=span_type,
        duration_ms=dur_ms,
        error=err,
        http_status=http_status,
        trace_id=_safe_str(_get_attr(attrs, "trace_id", "trace.id")),
        span_id=_safe_str(_get_attr(attrs, "span_id", "span.id")),
        peer_service=_safe_str(_get_attr(attrs, "peer.service", "peer_service")),
    )


def group_endpoint_stats(spans: Iterable[SpanView]) -> Dict[str, Dict[str, Any]]:
    by_res: Dict[str, List[SpanView]] = {}
    for s in spans:
        if not s.resource:
            continue
        by_res.setdefault(s.resource, []).append(s)

    out: Dict[str, Dict[str, Any]] = {}
    for res, xs in by_res.items():
        durs = [x.duration_ms for x in xs if x.duration_ms is not None]
        errs = [x for x in xs if x.error]
        out[res] = {
            "count": len(xs),
            "error_count": len(errs),
            "error_rate": (len(errs) / len(xs)) if xs else None,
            "p50_ms": percentile(durs, 50.0),
            "p95_ms": percentile(durs, 95.0),
            "p99_ms": percentile(durs, 99.0),
            "sample_trace_ids": [x.trace_id for x in xs if x.trace_id][:5],
        }
    return out


def dependency_key(span: SpanView) -> Optional[str]:
    if span.peer_service:
        return f"peer_service:{span.peer_service}"
    if span.span_type:
        # often "db", "redis", "http" - keep it coarse if nothing else exists
        return f"type:{span.span_type}"
    if span.name:
        return f"name:{span.name}"
    return None


def group_dependency_stats(spans: Iterable[SpanView]) -> Dict[str, Dict[str, Any]]:
    by_dep: Dict[str, List[SpanView]] = {}
    for s in spans:
        k = dependency_key(s)
        if not k:
            continue
        by_dep.setdefault(k, []).append(s)

    out: Dict[str, Dict[str, Any]] = {}
    for dep, xs in by_dep.items():
        durs = [x.duration_ms for x in xs if x.duration_ms is not None]
        errs = [x for x in xs if x.error]
        out[dep] = {
            "count": len(xs),
            "error_count": len(errs),
            "error_rate": (len(errs) / len(xs)) if xs else None,
            "total_duration_ms": sum(durs) if durs else None,
            "p95_ms": percentile(durs, 95.0),
            "sample_trace_ids": [x.trace_id for x in xs if x.trace_id][:5],
        }
    return out

