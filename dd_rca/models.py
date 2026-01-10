from __future__ import annotations

from dataclasses import asdict, dataclass, field
from typing import Any, Dict, List, Optional


@dataclass
class Symptom:
    type: str  # latency, error_rate, log_signature, metric
    query_or_signature: str
    baseline_value: Optional[float] = None
    incident_value: Optional[float] = None
    percent_change: Optional[float] = None
    peak_ts: Optional[str] = None
    peak_value: Optional[float] = None


@dataclass
class LogCluster:
    fingerprint: str
    template: str
    count_incident: int
    count_baseline: int
    first_seen: Optional[str] = None
    sample: Optional[Dict[str, Any]] = None


@dataclass
class EventItem:
    ts: str
    title: str
    text: str
    tags: List[str] = field(default_factory=list)
    url: Optional[str] = None


@dataclass
class Candidate:
    kind: str  # dependency, infrastructure, change, logs
    title: str
    score: float
    evidence: Dict[str, Any] = field(default_factory=dict)


@dataclass
class Report:
    meta: Dict[str, Any]
    windows: Dict[str, Any]
    scope: Dict[str, Any]
    symptoms: List[Symptom]
    findings: Dict[str, Any]
    recommendations: List[str]

    def to_dict(self) -> dict:
        d = asdict(self)
        # keep stable keys
        return {
            "meta": d["meta"],
            "windows": d["windows"],
            "scope": d["scope"],
            "symptoms": d["symptoms"],
            "findings": d["findings"],
            "recommendations": d["recommendations"],
        }

