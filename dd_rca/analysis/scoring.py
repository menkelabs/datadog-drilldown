from __future__ import annotations

from typing import List, Optional

from ..models import Candidate, LogCluster


def clamp01(x: float) -> float:
    if x < 0:
        return 0.0
    if x > 1:
        return 1.0
    return float(x)


def score_log_clusters(clusters: List[LogCluster], limit: int = 10) -> List[Candidate]:
    cands: List[Candidate] = []
    for c in clusters[: int(limit)]:
        inc = c.count_incident
        base = c.count_baseline
        # heuristic: new signatures score highest; otherwise ratio-based
        if base == 0 and inc > 0:
            score = 0.9 + min(0.1, inc / 200.0)
        elif base > 0:
            ratio = inc / float(base)
            score = min(0.9, max(0.0, (ratio - 1.0) / 5.0))  # 2x->0.2, 6x->1.0 cap
        else:
            score = 0.0
        cands.append(
            Candidate(
                kind="logs",
                title=f"Log signature spike: {c.template[:120]}",
                score=clamp01(score),
                evidence={
                    "fingerprint": c.fingerprint,
                    "template": c.template,
                    "count_incident": inc,
                    "count_baseline": base,
                    "first_seen": c.first_seen,
                    "sample": c.sample,
                },
            )
        )
    cands.sort(key=lambda x: x.score, reverse=True)
    return cands

