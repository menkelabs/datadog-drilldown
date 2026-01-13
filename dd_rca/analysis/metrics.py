from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Dict, List, Optional, Tuple


@dataclass(frozen=True)
class MetricSummary:
    value: Optional[float]
    peak_value: Optional[float]
    peak_ts: Optional[int]
    point_count: int


def _extract_points(series: Dict[str, Any]) -> List[Tuple[int, float]]:
    pts: List[Tuple[int, float]] = []
    pointlist = series.get("pointlist") or []
    for p in pointlist:
        if not isinstance(p, list) or len(p) < 2:
            continue
        ts, v = p[0], p[1]
        if ts is None or v is None:
            continue
        try:
            ts_i = int(ts / 1000) if int(ts) > 10_000_000_000 else int(ts)
            v_f = float(v)
        except Exception:
            continue
        pts.append((ts_i, v_f))
    return pts


def summarize_metrics_query(resp: Dict[str, Any]) -> MetricSummary:
    series_list = resp.get("series") or []
    all_pts: List[Tuple[int, float]] = []
    for s in series_list:
        if isinstance(s, dict):
            all_pts.extend(_extract_points(s))
    if not all_pts:
        return MetricSummary(value=None, peak_value=None, peak_ts=None, point_count=0)

    vals = [v for _, v in all_pts]
    avg = sum(vals) / float(len(vals))
    peak_ts, peak_val = max(all_pts, key=lambda tv: tv[1])
    return MetricSummary(value=avg, peak_value=peak_val, peak_ts=peak_ts, point_count=len(all_pts))


def percent_change(baseline: Optional[float], incident: Optional[float]) -> Optional[float]:
    if baseline is None or incident is None:
        return None
    if baseline == 0:
        return None
    return ((incident - baseline) / baseline) * 100.0

