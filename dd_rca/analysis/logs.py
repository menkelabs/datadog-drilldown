from __future__ import annotations

import hashlib
import re
from typing import Any, Dict, List, Optional, Tuple

from ..models import LogCluster


_UUID_RE = re.compile(r"\b[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\b", re.I)
_HEX_RE = re.compile(r"\b0x[0-9a-f]+\b", re.I)
_IP_RE = re.compile(r"\b\d{1,3}(?:\.\d{1,3}){3}\b")
_NUM_RE = re.compile(r"\b\d+\b")
_WS_RE = re.compile(r"\s+")


def _safe_str(x: Any) -> str:
    if x is None:
        return ""
    if isinstance(x, str):
        return x
    try:
        return str(x)
    except Exception:
        return ""


def normalize_message(msg: str) -> str:
    s = _safe_str(msg).strip()
    s = _UUID_RE.sub("<uuid>", s)
    s = _HEX_RE.sub("<hex>", s)
    s = _IP_RE.sub("<ip>", s)
    s = _NUM_RE.sub("<num>", s)
    s = _WS_RE.sub(" ", s)
    return s[:500]


def fingerprint(template: str) -> str:
    h = hashlib.sha1(template.encode("utf-8", errors="ignore")).hexdigest()
    return h[:12]


def _log_timestamp_iso(log_item: Dict[str, Any]) -> Optional[str]:
    attrs = log_item.get("attributes") or {}
    ts = attrs.get("timestamp")
    if isinstance(ts, str) and ts:
        return ts
    # fallback to "id" / nothing
    return None


def cluster_logs(logs: List[Dict[str, Any]]) -> Dict[str, LogCluster]:
    clusters: Dict[str, LogCluster] = {}
    for item in logs or []:
        attrs = item.get("attributes") or {}
        message = attrs.get("message") or attrs.get("msg") or ""

        # Prefer structured error fields when present (better clustering than raw message).
        # Common shapes:
        # - error.type / error.message / error.stack
        # - exception / stack_trace
        err_type = attrs.get("error.type") or attrs.get("error", {}).get("type") if isinstance(attrs.get("error"), dict) else None
        err_msg = attrs.get("error.message") or attrs.get("error", {}).get("message") if isinstance(attrs.get("error"), dict) else None
        err_stack = attrs.get("error.stack") or attrs.get("error", {}).get("stack") if isinstance(attrs.get("error"), dict) else None

        if not err_type and isinstance(attrs.get("exception"), str):
            err_type = attrs.get("exception")
        if not err_stack and isinstance(attrs.get("stack_trace"), str):
            err_stack = attrs.get("stack_trace")

        template_parts = []
        if err_type:
            template_parts.append(f"type={normalize_message(_safe_str(err_type))}")
        if err_msg:
            template_parts.append(f"msg={normalize_message(_safe_str(err_msg))}")
        else:
            template_parts.append(f"msg={normalize_message(_safe_str(message))}")

        stack_hash = None
        if isinstance(err_stack, str) and err_stack.strip():
            # hash full stack, but keep the template short
            stack_hash = hashlib.sha1(err_stack.encode("utf-8", errors="ignore")).hexdigest()[:12]
            template_parts.append(f"stack={stack_hash}")

        template = " | ".join([p for p in template_parts if p])[:500]
        fp = fingerprint(template)

        ts = _log_timestamp_iso(item)
        sample = None
        if fp not in clusters:
            # keep representative sample small
            sample = {
                "timestamp": ts,
                "service": attrs.get("service"),
                "host": attrs.get("host"),
                "message": _safe_str(message)[:1000],
                "error_type": _safe_str(err_type)[:200] if err_type else None,
                "error_message": _safe_str(err_msg)[:500] if err_msg else None,
                "stack_hash": stack_hash,
            }
            clusters[fp] = LogCluster(
                fingerprint=fp,
                template=template,
                count_incident=0,
                count_baseline=0,
                first_seen=ts,
                sample=sample,
            )

        c = clusters[fp]
        c.count_incident += 1
        if ts and (c.first_seen is None or ts < c.first_seen):
            c.first_seen = ts
    return clusters


def merge_baseline_counts(
    incident_clusters: Dict[str, LogCluster],
    baseline_logs: List[Dict[str, Any]],
) -> Dict[str, LogCluster]:
    baseline_clusters = cluster_logs(baseline_logs)
    for fp, base in baseline_clusters.items():
        if fp in incident_clusters:
            incident_clusters[fp].count_baseline = base.count_incident
        else:
            # keep baseline-only clusters out of report for MVP
            pass
    return incident_clusters


def rank_clusters(clusters: Dict[str, LogCluster], limit: int = 10) -> List[LogCluster]:
    items = list(clusters.values())

    def score(c: LogCluster) -> Tuple[float, int]:
        # prioritize new or spiking signatures
        inc = c.count_incident
        base = c.count_baseline
        if base == 0 and inc > 0:
            ratio = 9999.0
        else:
            ratio = inc / float(base) if base > 0 else 0.0
        return (ratio, inc)

    items.sort(key=lambda c: score(c), reverse=True)
    return items[: int(limit)]

