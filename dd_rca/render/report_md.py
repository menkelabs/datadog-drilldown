from __future__ import annotations

from typing import Any, Dict, List


def _md_kv(k: str, v: Any) -> str:
    if v is None:
        return ""
    return f"- **{k}**: {v}"


def render_report_md(report: Dict[str, Any]) -> str:
    meta = report.get("meta") or {}
    windows = report.get("windows") or {}
    scope = report.get("scope") or {}
    symptoms = report.get("symptoms") or []
    findings = report.get("findings") or {}
    recs = report.get("recommendations") or []

    lines: List[str] = []
    lines.append("## dd-rca report\n")

    lines.append("### Meta")
    for k in ("seed_type", "generated_at", "dd_site"):
        if meta.get(k) is not None:
            lines.append(_md_kv(k, meta.get(k)))
    lines.append("")

    lines.append("### Time windows")
    inc = (windows.get("incident") or {})
    base = (windows.get("baseline") or {})
    lines.append(_md_kv("incident_start", inc.get("start")))
    lines.append(_md_kv("incident_end", inc.get("end")))
    lines.append(_md_kv("baseline_start", base.get("start")))
    lines.append(_md_kv("baseline_end", base.get("end")))
    lines.append("")

    lines.append("### Scope")
    for k, v in scope.items():
        if v:
            lines.append(_md_kv(k, v))
    lines.append("")

    lines.append("### Symptoms")
    for s in symptoms:
        if not isinstance(s, dict):
            continue
        lines.append(f"- **{s.get('type')}**: `{s.get('query_or_signature','')}`")
        if s.get("baseline_value") is not None or s.get("incident_value") is not None:
            lines.append(f"  - baseline: {s.get('baseline_value')}")
            lines.append(f"  - incident: {s.get('incident_value')}")
        if s.get("percent_change") is not None:
            lines.append(f"  - change: {s.get('percent_change'):.2f}%")
        if s.get("peak_ts") is not None:
            lines.append(f"  - peak: {s.get('peak_value')} @ {s.get('peak_ts')}")
    lines.append("")

    lines.append("### Top candidates")
    candidates = findings.get("candidates") or []
    for c in candidates[:10]:
        if not isinstance(c, dict):
            continue
        lines.append(f"- **{c.get('kind')}** (score {c.get('score')}): {c.get('title')}")
    lines.append("")

    lines.append("### Events")
    for e in (findings.get("events") or [])[:20]:
        if not isinstance(e, dict):
            continue
        lines.append(f"- **{e.get('ts')}**: {e.get('title')}")
    lines.append("")

    if recs:
        lines.append("### Recommendations")
        for r in recs:
            lines.append(f"- {r}")
        lines.append("")

    return "\n".join([ln for ln in lines if ln is not None])

